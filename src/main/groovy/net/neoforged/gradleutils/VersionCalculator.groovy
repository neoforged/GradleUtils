/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.transform.PackageScope
import net.neoforged.gradleutils.specs.VersionSpec
import org.eclipse.jgit.api.DescribeCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
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

    String calculate(Git git, String rev = Constants.HEAD, boolean skipVersionPrefix = false, boolean skipBranchSuffix = false) {
        final describe = findTag(git, rev)

        String tag = describe.tag
        // Strip label from tag
        if (spec.tags.stripTagLabel.get()) {
            final sepIdx = describe.tag.lastIndexOf(GENERAL_SEPARATOR as int)
            if (sepIdx != -1) {
                tag = describe.tag.substring(0, sepIdx)
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
            version.append(VERSION_SEPARATOR).append(describe.offset)
        }

        if (describe.label != null) {
            version.append(GENERAL_SEPARATOR).append(describe.label)
        }

        if (!skipBranchSuffix && spec.branches.suffixBranch.get()) {
            @Nullable final branchSuffix = getBranchSuffix(git)
            if (branchSuffix != null) {
                version.append(GENERAL_SEPARATOR).append(branchSuffix)
            }
        }

        return version.toString()
    }

    private DescribeOutput findTag(Git git, String startingRev) {
        TagContextImpl context = new TagContextImpl()
        context.label = spec.tags.label.getOrNull()
        int trackedCommitCount = 0
        String currentRev = startingRev

        while (true) {
            final described = describe(git).setTarget(currentRev).call()
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
    private String getBranchSuffix(Git git) {
        final head = git.repository.exactRef('HEAD')
        // Matches Repository.getFullBranch() but returning null when on a detached HEAD
        final longBranch = head.symbolic ? head?.target?.name : null

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

    @Immutable
    static class DescribeOutput {
        final String tag
        final int offset
        final String label
    }

    private DescribeCommand describe(Git git) {
        final includeFilters = spec.tags.includeFilters.get().<String> toArray(new String[0])
        return git.describe()
                .setLong(true)
                .setTags(spec.tags.includeLightweightTags.get())
                .setMatch(includeFilters)
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
