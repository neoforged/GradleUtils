/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.transform.PackageScope
import net.neoforged.gradleutils.git.GitProvider
import net.neoforged.gradleutils.specs.VersionSpec
import org.eclipse.jgit.fnmatch.FileNameMatcher
import org.eclipse.jgit.lib.Repository
import org.gradle.api.GradleException

import javax.annotation.Nullable

// Responsible for actually calculating the version
@CompileStatic
@PackageScope
class VersionCalculator {
    @PackageScope
    static final char GENERAL_SEPARATOR = '-'
    @PackageScope
    static final char VERSION_SEPARATOR = '.'

    private final VersionSpec spec

    VersionCalculator(VersionSpec spec) {
        this.spec = spec
    }

    String calculate(GitProvider git, String rev = "HEAD", boolean skipVersionPrefix = false, boolean skipBranchSuffix = false) {
        final describe = findTag(git, rev)

        return calculateForTag(
                git,
                describe.tag,
                describe.label,
                describe.offset,
                skipVersionPrefix,
                skipBranchSuffix
        )
    }

    String calculateForTag(GitProvider git, String tag, String label, int offset, boolean skipVersionPrefix = false, boolean skipBranchSuffix = false) {
        // Strip label from tag
        if (spec.tags.stripTagLabel.get()) {
            final sepIdx = tag.lastIndexOf(GENERAL_SEPARATOR as int)
            if (sepIdx != -1) {
                tag = tag.substring(0, sepIdx)
            }
        }

        // [<prefix>.]<tag>[.<offset>][-<label>][-<branch>]
        StringBuilder version = new StringBuilder()

        @Nullable final prefix = spec.versionPrefix.getOrNull()
        if (!skipVersionPrefix && prefix != null) {
            version.append(prefix).append(GENERAL_SEPARATOR)
        }

        version.append(tag)

        if (spec.tags.appendCommitOffset.get()) {
            version.append(VERSION_SEPARATOR).append(offset)
        }

        if (label != null) {
            version.append(GENERAL_SEPARATOR).append(label)
        }

        if (!skipBranchSuffix && spec.branches.suffixBranch.get()) {
            @Nullable final branchSuffix = getBranchSuffix(git)
            if (branchSuffix != null) {
                version.append(GENERAL_SEPARATOR).append(branchSuffix)
            }
        }

        return version.toString()
    }

    String getDefaultLabel() {
        return spec.tags.label.getOrNull()
    }

    @Nullable
    String getTagLabel(String tagName) {
        if (spec.tags.extractLabel.get()) {
            final int separatorIndex = tagName.lastIndexOf(GENERAL_SEPARATOR as int)
            // TODO: should we ignore empty labels? (i.e. `1.0-`)
            if (separatorIndex != -1) {
                return tagName.substring(separatorIndex + 1)
            }
        }
        return null
    }

    boolean isLabelResetTag(String tagName) {
        final cleanLabel = spec.tags.cleanMarkerLabel.getOrNull()
        return cleanLabel != null && tagName.endsWith(GENERAL_SEPARATOR.toString() + cleanLabel)
    }

    private DescribeOutput findTag(GitProvider git, String startingRev) {
        TagContextImpl context = new TagContextImpl()
        context.label = defaultLabel
        int trackedCommitCount = 0
        String currentRev = startingRev

        while (true) {
            final described = describe(git).target(currentRev).run()
            if (described === null) {
                throw new GradleException("Cannot calculate the project version without a previous Git tag. Did you forget to run \"git fetch --tags\"?")
            }

            // Describe (long) output is "<tag>-<offset>-g<commit>"
            final describeSplit = GradleUtils.rsplit(described, '-', 2)

            context.tag = describeSplit[0]
            trackedCommitCount += Integer.parseUnsignedInt(describeSplit[1])
            if (spec.tags.extractLabel.get()) {
                final int separatorIndex = context.tag.lastIndexOf(GENERAL_SEPARATOR as int)
                // TODO: should we ignore empty labels? (i.e. `1.0-`)
                if (separatorIndex != -1) {
                    context.label = context.tag.substring(separatorIndex + 1)
                }
            }

            @Nullable final markerLabel = spec.tags.cleanMarkerLabel.getOrNull()
            if (markerLabel == null || !context.endsWithLabel(markerLabel)) break
            // Tag ends with the clean marker label -- reset label to null and continue searching
            context.label = null

            // Because JGit doesn't provide the equivalent to '--exclude', we have to manually go about this
            // by searching the parent of the current tag, which is why we track the commit count

            // nth ancestor selector (~2 meaning grandparent)
            currentRev = context.tag + '~1'
            // This accounts for the commit at the current tag (which is skipped due to using tag's parent)
            trackedCommitCount += 1
        }

        if (context.tag.isEmpty()) throw new RuntimeException("Somehow got left with no tag?")
        return new DescribeOutput(context.tag, trackedCommitCount, context.label)
    }

    @Nullable
    private String getBranchSuffix(GitProvider git) {
        final longBranch = git.fullBranch

        String branch = longBranch != null ? Repository.shortenRefName(longBranch) : ''
        if (branch in spec.branches.suffixExemptedBranches.get()) {
            // Branch is exempted from suffix
            return null
        }

        // Convert GH pull request refs names (pulls/<#>/head) to a smaller format, without the /head
        if (branch?.startsWith('pulls/'))
            branch = 'pr' + GradleUtils.rsplit(branch, '/', 1)[1]
        branch = branch?.replaceAll(/[\\\/]/, '-')
        return branch
    }

    boolean isIncludedTag(String tagName) {
        final filters = spec.tags.includeFilters.get()
        if (filters.isEmpty()) {
            return true
        }

        for (final def filter in filters) {
            if (new FileNameMatcher(filter, null).append(tagName)) {
                return true
            }
        }
        return false
    }

    @Immutable
    static class DescribeOutput {
        final String tag
        final int offset
        final String label
    }

    private GitProvider.DescribeCall describe(GitProvider git) {
        final includeFilters = spec.tags.includeFilters.get().<String> toArray(new String[0])
        return git.describe()
                .longFormat(true)
                .includeLightweightTags(spec.tags.includeLightweightTags.get())
                .matching(includeFilters)
    }

    static class TagContextImpl {
        @Nullable
        String label = null
        String tag = ""

        boolean endsWithLabel(String label) {
            return this.tag.endsWith(GENERAL_SEPARATOR.toString() + label)
        }
    }
}
