/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import net.neoforged.gradleutils.git.GitProvider
import org.eclipse.jgit.lib.Constants

@CompileStatic
@PackageScope
class ChangelogGenerator {
    private final VersionCalculator calculator

    ChangelogGenerator(VersionCalculator calculator) {
        this.calculator = calculator
    }

    String generate(GitProvider git, String earliest, String latest = Constants.HEAD) {
        def commits = git.getCommits(latest, earliest)

        // TODO: headers for tags -- need more hooks into version calculator
        // TODO: caching for version calculation -- perhaps split version calculator to two passes? 

        final StringBuilder builder = new StringBuilder()
        for (GitProvider.CommitData commit : commits) {

            final version = calculateVersion(git, commit.hash())
            // " - `<version>` <message>" 
            // "   <continuation>" if multi-line

            builder.append(" - `$version` ")
            buildCommitMessage(builder, commit.message(), "   ")
        }

        return builder.toString()
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

    private String calculateVersion(GitProvider git, String rev) {
        // Skip branch suffix
        return calculator.calculate(git, rev, true, true)
    }
}
