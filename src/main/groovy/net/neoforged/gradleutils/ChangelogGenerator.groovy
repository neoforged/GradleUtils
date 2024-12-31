/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import net.neoforged.gradleutils.git.GitProvider
import net.neoforged.gradleutils.specs.VersionSpec
import org.eclipse.jgit.lib.Constants
import org.jetbrains.annotations.Nullable

@CompileStatic
@PackageScope
class ChangelogGenerator {
    private final VersionCalculator calculator
    private final VersionSpec versionSpec

    ChangelogGenerator(VersionCalculator calculator, VersionSpec versionSpec) {
        this.calculator = calculator
        this.versionSpec = versionSpec
    }

    String generate(GitProvider git, @Nullable String earliest, String latest = Constants.HEAD) {

        // Map of Commit -> Tags
        Map<String, List<String>> tags = git.getTags(versionSpec.tags.includeLightweightTags.get())
                .findAll { calculator.isIncludedTag(it.name()) }
                .groupBy { it.hash() }
                .collectEntries { k, v ->
                    [(k): v.collect { it.name() }]
                }

        def commits = git.getCommits(latest, earliest)

        def versions = buildCommitToVersionMap(commits, tags, git)

        // TODO: headers for tags -- need more hooks into version calculator
        // TODO: caching for version calculation -- perhaps split version calculator to two passes? 

        final StringBuilder builder = new StringBuilder()
        String currentMajor = ""

        for (GitProvider.CommitData commit : commits) {
            final version = versions.get(commit.hash())
            // " - `<version>` <message>" 
            // "   <continuation>" if multi-line

            if (version) {
                String majorVersion = version.split("\\.")[0..1].join(".")
                if (majorVersion != currentMajor) {
                    builder.append("\n")
                    builder.append("# $majorVersion")
                    builder.append("\n\n")
                    currentMajor = majorVersion
                }

                builder.append(" - `$version` ")
            } else {
                // This might be a commit before first tag
                builder.append(" - `${commit.shortHash()}` ")
            }
            buildCommitMessage(builder, commit.message(), "   ")
        }

        return builder.toString()
    }

    private Map<String, String> buildCommitToVersionMap(List<GitProvider.CommitData> commits, Map<String, List<String>> tags, GitProvider git) {
        var result = new HashMap<String, String>()
        // Work on each entry in reverse
        int prevTagAt = -1
        String prevTag = null
        String label = null
        for (int i = commits.size() - 1; i >= 0; i--) {
            final commit = commits[i]
            final commitTags = tags.getOrDefault(commit.hash(), [])
            for (var tag in commitTags) {
                if (calculator.isLabelResetTag(tag)) {
                    label = null
                    continue
                }

                var tagLabel = calculator.getTagLabel(tag)
                if (tagLabel != null) {
                    // found a label for the current anchor
                    label = tagLabel
                } else {
                    // new version anchor found
                    prevTagAt = i
                    prevTag = tag
                    label = calculator.defaultLabel
                }
                break
            }

            if (prevTag != null) {
                final offset = prevTagAt - i
                result[commit.hash()] = calculator.calculateForTag(git, prevTag, label, offset, true, true)
            }
        }
        return result
    }

    private static void buildCommitMessage(StringBuilder builder, String message, String continueHeader) {
        // Assume the current line in the builder already contains the initial part of the line (with the version)

        // Assume that the message contains at least one LF
        // If the first and last LF in the message are at the same position, then there is only one singular LF
        if (message.indexOf('\n') == message.lastIndexOf('\n')) {
            // Single-line message -- append to the line (it already has a terminator)
            builder.append(message)
        } else {
            // Multi-line message -- append each line, with the 2nd line and onwards prefixed with the continueHeader
            boolean first = true
            for (String line : message.split(/\n/)) {
                if (!first) {
                    builder.append(continueHeader)
                }
                first = false
                builder.append(line).append('\n') // Since the LF was removed by the split operation 
            }
        }
    }
}
