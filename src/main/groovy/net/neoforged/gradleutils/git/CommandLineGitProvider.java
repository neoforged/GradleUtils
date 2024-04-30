/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.git;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * A provider which uses the command line {@code git} command.
 */
public class CommandLineGitProvider implements GitProvider {
    private final File directory;

    @Nullable
    public static GitProvider create(File directory) {
        // Check if the git command exists and a git repo exists
        try {
            runGit(directory, false, builder -> builder.command("rev-parse", "--is-inside-work-tree"), process -> null);
        } catch (Exception ignored) {
            // Either the git command is not present or there is no git repo -- quit in both cases
            return null;
        }
        return new CommandLineGitProvider(directory);
    }

    CommandLineGitProvider(File directory) {
        this.directory = directory;
    }

    @Override
    public File getDotGitDirectory() {
        return runGit(true,
                builder -> builder.command("rev-parse", "--absolute-git-dir"),
                process -> new File(readSingleLine(process)));
    }

    @Override
    public String abbreviateRef(String ref, int minimumLength) {
        if (minimumLength != 0 && minimumLength < 4) {
            throw new IllegalArgumentException("Minimum length must either be 0, or equal or greater than 4: " + minimumLength);
        }
        return runGit(true,
                builder -> {
                    builder.command("rev-parse");
                    if (minimumLength != 0) builder.command().add("--short=" + minimumLength);
                    builder.command().add(ref);
                },
                CommandLineGitProvider::readSingleLine);
    }

    @Override
    public String shortenRef(String ref) {
        return runGit(true,
                builder -> builder.command("rev-parse", "--symbolic-ref", ref),
                CommandLineGitProvider::readSingleLine);
    }

    @Override
    public String getHead() {
        return runGit(true,
                builder -> builder.command("rev-parse", "HEAD"),
                CommandLineGitProvider::readSingleLine);
    }

    @Nullable
    @Override
    public String getFullBranch() {
        return runGit(false,
                builder -> builder.command("symbolic-ref", "HEAD"),
                process -> {
                    if (process.exitValue() != 0) return null;
                    return readSingleLine(process);
                });
    }

    @Nullable
    @Override
    public String getRemotePushUrl(String remoteName) {
        return runGit(false,
                builder -> builder.command("remote", "get-url", "--push", remoteName),
                process -> {
                    if (process.exitValue() != 0) return null;
                    return readSingleLine(process);
                });
    }

    @Override
    public int getRemotesCount() {
        final Integer ret = runGit(true,
                builder -> {
                    builder.command("remote");
                },
                process -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        return (int) reader.lines().count();
                    }
                });
        return ret != null ? ret : 0;
    }

    @Override
    public List<CommitData> getCommits(String latestRev, String earliestRev) {
        throw new UnsupportedOperationException("Not yet implemented"); 
        // TODO: implement with two invocations (append)
        // git log --ignore-missing --no-show-signature --pretty=format:%H%n%B -z latest ^earliest
        // git log --ignore-missing --no-show-signature --pretty=format:%H%n%B -z earliest
    }

    @Override
    public DescribeCall describe() {
        return new DescribeCallImpl();
    }

    @Override
    public void close() {
        // No operation -- we have nothing to close
    }

    class DescribeCallImpl extends AbstractDescribeCall {
        @Override
        public String run() {
            return runGit(true,
                    builder -> {
                        builder.command("describe");

                        if (longFormat) builder.command().add("--long");
                        if (includeLightweightTags) builder.command().add("--tags");

                        for (String pattern : matchPatterns) {
                            builder.command().add("--match");
                            builder.command().add(pattern);
                        }

                        builder.command(target);
                    },
                    CommandLineGitProvider::readSingleLine);
        }
    }

    private <R> R runGit(boolean checkSuccess,
                         Consumer<ProcessBuilder> processBuilder,
                         ThrowingFunction<Process, R> outputFunction) {
        return runGit(this.directory, checkSuccess, processBuilder, outputFunction);
    }

    private static <R> R runGit(File directory,
                                boolean checkSuccess,
                                Consumer<ProcessBuilder> processBuilder,
                                ThrowingFunction<Process, R> outputFunction) {
        final ProcessBuilder builder = new ProcessBuilder()
                .directory(directory);
        processBuilder.accept(builder);

        builder.command().add(0, "git");

        try {
            final Process process = builder.start();
            final int exitCode = process.waitFor();
            if (checkSuccess && exitCode != 0) throw new IOException("Command did not return successfully");

            return outputFunction.apply(process);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to run " + builder.command(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run " + builder.command(), e);
        }
    }

    private static String readSingleLine(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return reader.readLine();
        }
    }

    private interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }
}
