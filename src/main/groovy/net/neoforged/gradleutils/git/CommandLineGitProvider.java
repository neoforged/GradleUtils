/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.git;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logging;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A provider which uses the command line {@code git} command.
 */
public class CommandLineGitProvider implements GitProvider {
    private static final Logger LOGGER = Logging.getLogger(CommandLineGitProvider.class);

    private final File directory;

    @Nullable
    public static GitProvider create(File directory) {
        // Check if the git command exists and a git repo exists
        try {
            runGit(directory, false, Arrays.asList("rev-parse", "--is-inside-work-tree"), (exitCode, stdout) -> null);
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
        return new File(runGitReadLine("rev-parse", "--absolute-git-dir"));
    }

    @Override
    public String abbreviateRef(String ref, int minimumLength) {
        if (minimumLength != 0 && minimumLength < 4) {
            throw new IllegalArgumentException("Minimum length must either be 0, or equal or greater than 4: " + minimumLength);
        }

        List<String> args = new ArrayList<>();
        args.add("rev-parse");
        if (minimumLength != 0) args.add("--short=" + minimumLength);
        args.add(ref);

        return runGitReadLine(args);
    }

    @Override
    public String shortenRef(String ref) {
        return runGitReadLine("rev-parse", "--symbolic-ref", ref);
    }

    @Override
    public String getHead() {
        return runGitReadLine("rev-parse", "HEAD");
    }

    @Nullable
    @Override
    public String getFullBranch() {
        return runGit(false,
                Arrays.asList("symbolic-ref", "HEAD"),
                (exitCode, stdout) -> {
                    if (exitCode != 0) return null;
                    return readSingleLine(exitCode, stdout);
                });
    }

    @Nullable
    @Override
    public String getRemotePushUrl(String remoteName) {
        return runGit(false,
                Arrays.asList("remote", "get-url", "--push", remoteName),
                (exitCode, stdout) -> {
                    if (exitCode != 0) return null;
                    return readSingleLine(exitCode, stdout);
                });
    }

    @Override
    public int getRemotesCount() {
        final Integer ret = runGit(true,
                Collections.singletonList("remote"),
                (exitCode, stdout) -> stdout.size());
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
            List<String> args = new ArrayList<>();
            args.add("describe");

            if (longFormat) args.add("--long");
            if (includeLightweightTags) args.add("--tags");

            for (String pattern : matchPatterns) {
                args.add("--match");
                args.add(pattern);
            }

            args.add(target);            

            return runGit(true, args, CommandLineGitProvider::readSingleLine);
        }
    }

    public interface ProcessResultProcessor<T> {
        T process(int exitCode, List<String> stdout);
    }

    private <R> R runGit(boolean checkSuccess,
                         List<String> args,
                         ProcessResultProcessor<R> outputFunction) {
        return runGit(this.directory, checkSuccess, args, outputFunction);
    }

    private String runGitReadLine(List<String> args) {
        return runGit(this.directory, true, args, CommandLineGitProvider::readSingleLine);
    }

    private String runGitReadLine(String... args) {
        return runGitReadLine(Arrays.asList(args));
    }

    private static <R> R runGit(File directory,
                                boolean checkSuccess,
                                List<String> args,
                                ProcessResultProcessor<R> outputFunction) {
        List<String> command = new ArrayList<>(args);
        command.add(0, "git");

        ProcessBuilder builder = new ProcessBuilder(command).directory(directory);

        StringBuilder combinedArgs = printableCommand(builder.command());
        LOGGER.info("Running git command: {}", combinedArgs);

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new GradleException("Failed to start " + combinedArgs + ": " + e.getMessage());
        }


        // Combined output is easier for debugging problems
        StringBuilder combinedOutput = new StringBuilder();
        // Stdout is easier for parsing output in the successful case
        List<String> stdout = new ArrayList<>();
        int exitCode;

        try {
            // We provide no STDIN to the process
            process.getOutputStream().close();

            Thread stdoutReader = startLineReaderThread(process.getInputStream(), line -> {
                stdout.add(line);
                synchronized (combinedOutput) {
                    combinedOutput.append(line);
                }
            });
            Thread stderrReader = startLineReaderThread(process.getErrorStream(), line -> {
                synchronized (combinedOutput) {
                    combinedOutput.append(line);
                }
            });

            exitCode = process.waitFor();

            stderrReader.join();
            stdoutReader.join();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to run " + combinedArgs + ": " + e, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run " + combinedArgs + ": " + e, e);
        }

        if (checkSuccess && exitCode != 0) {
            throw new RuntimeException("Failed running '" + combinedArgs + "'. Exit Code " + exitCode
                    + ", Output: " + combinedOutput);
        }

        return outputFunction.process(exitCode, stdout);
    }

    // This is not fully accurate as Java will escape arguments in a platform-specific way. But this at least
    // gets us a copy-pastable command in 90% of cases.
    private static StringBuilder printableCommand(List<String> args) {
        StringBuilder result = new StringBuilder();
        for (String arg : args) {
            if (result.length() > 0) {
                result.append(' ');
            }
            if (arg.contains(" ")) {
                result.append('"').append(arg).append('"');
            } else {
                result.append(arg);
            }
        }
        return result;
    }

    private static Thread startLineReaderThread(InputStream stream, Consumer<String> lineHandler) {
        Thread stdoutReader = new Thread(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, getNativeCharset()));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineHandler.accept(line);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                try {
                    stream.close();
                } catch (IOException e) {
                    LOGGER.error("Failed to close process output stream.", e);
                }
            }
        });
        stdoutReader.setUncaughtExceptionHandler((t, e) -> LOGGER.error("Failed to read output of external process.", e));
        stdoutReader.setDaemon(true);
        stdoutReader.start();
        return stdoutReader;
    }

    private static String readSingleLine(int exitCode, List<String> stdout) {
        return stdout.get(0).trim();
    }

    /**
     * Get the platform native charset. To see how this differs from the default charset,
     * see https://openjdk.org/jeps/400. This property cannot be overriden via system
     * property.
     */
    private static Charset getNativeCharset() {
        return NativeEncodingHolder.charset;
    }

    private static class NativeEncodingHolder {
        static final Charset charset;

        static {
            String nativeEncoding = System.getProperty("native.encoding");
            if (nativeEncoding == null) {
                // In Pre-JDK17 we can only fall back to undocumented properties to get the real charset
                nativeEncoding = System.getProperty("sun.stdout.encoding");
                if (nativeEncoding == null) {
                    LOGGER.error("Failed to determine native character set on Pre-JDK17. Falling back to default charset.");
                    nativeEncoding = Charset.defaultCharset().name();
                }
            }
            charset = Charset.forName(nativeEncoding);
        }
    }
}
