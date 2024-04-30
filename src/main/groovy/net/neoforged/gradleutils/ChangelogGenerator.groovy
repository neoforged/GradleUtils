/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import net.neoforged.gradleutils.git.GitProvider
import net.neoforged.gradleutils.git.JGitProvider
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk

@CompileStatic
@PackageScope
class ChangelogGenerator {
    private final VersionCalculator calculator

    ChangelogGenerator(VersionCalculator calculator) {
        this.calculator = calculator
    }

    String generate(Git git, String earliest, String latest = Constants.HEAD) {
        // Resolve both commits
        final RevCommit earliestCommit, latestCommit
        try (RevWalk walk = new RevWalk(git.repository)) {
            earliestCommit = walk.parseCommit(git.repository.resolve(earliest))
            latestCommit = walk.parseCommit(git.repository.resolve(latest))
        }

        // List all commits between latest and earliest commits -- including the two ends
        def logCommand = git.log().add(latestCommit)
        // Exclude all parents of earliest commit
        for (RevCommit parent : earliestCommit.getParents()) {
            logCommand.not(parent)
        }

        // List has order of latest (0) to earliest (list.size())
        final List<RevCommit> commits = logCommand.call().collect()

        // TODO: headers for tags -- need more hooks into version calculator
        // TODO: caching for version calculation -- perhaps split version calculator to two passes? 

        final StringBuilder builder = new StringBuilder()
        for (RevCommit commit : commits) {

            final version = calculateVersion(git, commit.name())
            // " - `<version>` <message>" 
            // "   <continuation>" if multi-line

            builder.append(" - `$version` ")
            buildCommitMessage(builder, commit, "   ")
        }

        return builder.toString()
    }

    private static void buildCommitMessage(StringBuilder builder, RevCommit commit, String continueHeader) {
        // Assume the current line in the builder already contains the initial part of the line (with the version)

        final message = commit.fullMessage
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

    private String calculateVersion(Git git, String rev) {
        // TODO: switch this class fully to GitProvider
        JGitProvider provider = new JGitProvider(git.repository)
        GitProvider 
        // Skip branch suffix
        return calculator.calculate(provider, rev, true, true)
    }
}
