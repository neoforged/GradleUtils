/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.git;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

/**
 * Provides a simplified interface to the Git version control system.
 *
 * <p>This is intended to be implemented with various backends, such as {@link CommandLineGitProvider} using the
 * command-line {@code git} (when present) and {@link JGitProvider} using the JGit library, as a fallback.</p>
 *
 * <p>The main purpose of this is to allow for better, more feature-complete backends to be used when available, and
 * falling back to a less-feature-complete but known-working backend. For example, the JGit library
 * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=477475">lacks support for work trees</a>, which the
 * command-line {@code git} is fully capable of using.</p>
 */
public interface GitProvider extends AutoCloseable {
    File getDotGitDirectory();

    /**
     * Returns an abbreviated SHA-1 commit ID for the given ref.
     *
     * @param ref           the ref
     * @param minimumLength the minimum amount of characters, or {@code 0} to default to a reasonable value no lower than 4
     * @return an abbreviated SHA-1 commit ID for the given ref
     * @throws IllegalArgumentException if the minimum length is not 0 and is negative or smaller than 4
     */
    String abbreviateRef(String ref, int minimumLength);

    /**
     * Returns a shortened user-friendlier version of the given ref.
     *
     * @param ref the ref
     * @return a shortened user-friendlier version of the given ref
     */
    String shortenRef(String ref);

    /**
     * Returns the commit ID for the revision pointed at by HEAD.
     *
     * @return the commit ID for the revision pointed at by HEAD
     */
    String getHead();

    /**
     * Returns the full branch name (with prefix) pointed at by HEAD, or {@code null} if not available (such as during
     * a detached HEAD state.
     *
     * @return the full branch name (with prefix) pointed at by HEAD, or {@code null} if not available (such as during
     * a detached HEAD state
     */
    @Nullable
    String getFullBranch();

    /**
     * Returns the push URL for a remote, or {@code null} if the remote doesn't exist.
     *
     * @return the push URL for a remote, or {@code null} if the remote doesn't exist
     */
    @Nullable
    String getRemotePushUrl(String remoteName);

    /**
     * Returns the number of remotes in the repository.
     *
     * @return the number of remotes in the repository
     */
    int getRemotesCount();

    /**
     * Returns the commit information for the given revs, starting from the latest revision to (and including)
     * the earliest revision.
     *
     * <p>Note that this is not equivalent to a command invocation of {@code git log {latest} ^{earliest}}, as this
     * method will also include the information about the earliest commit.</p>
     *
     * @param latestRev   the latest revision
     * @param earliestRev the earliest revision
     * @return the commit information for the given refs,  starting from the latest revision to (and including)
     * the earliest revision
     */
    List<CommitData> getCommits(String latestRev, String earliestRev);

    class CommitData {
        private final String hash;
        private final String message;

        CommitData(String hash, String message) {
            this.hash = hash;
            this.message = message;
        }

        public String hash() {
            return hash;
        }

        public String message() {
            return message;
        }
    }

    /**
     * Starts a describe call.
     *
     * @return a describe call
     */
    DescribeCall describe();

    /**
     * A describe call.
     */
    interface DescribeCall {
        /**
         * Sets whether to always use the long format.
         *
         * @param longFormat whether to always use the long format
         * @return this object, for method chaining
         */
        DescribeCall longFormat(boolean longFormat);

        /**
         * Sets whether to include lightweight tags when calculating for the nearest tag.
         *
         * @param lightweight whether to include lightweight tags
         * @return this object, for method chaining
         */
        DescribeCall includeLightweightTags(boolean lightweight);

        /**
         * Configures glob patterns to match when looking for certain tags. If this method is called with no arguments,
         * then all existing patterns are cleared.
         *
         * @param patterns an array of glob patterns, which may be empty
         * @return this object, for method chaining
         */
        DescribeCall matching(String... patterns);

        // TODO: document
        DescribeCall target(String rev);

        /**
         * Returns the result of the describe call.
         *
         * @return the result of the describe call
         */
        String run();
    }
}
