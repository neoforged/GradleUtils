/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.git;

import org.eclipse.jgit.api.DescribeCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RemoteConfig;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A provider which uses JGit, a pure Java implementation of Git.
 *
 * @see Git
 */
public class JGitProvider implements GitProvider {
    private final Repository repository;
    private final Git git;

    @Nullable
    public static GitProvider create(File directory) {
        try {
            final Repository repo = new RepositoryBuilder()
                    .findGitDir(directory)
                    .setMustExist(true)
                    .build();

            return new JGitProvider(repo);
        } catch (Exception ignored) {
            return null;
        }
    }

    public JGitProvider(Repository repository) {
        this.repository = repository;
        this.git = Git.wrap(repository);
    }

    @Override
    public void close() {
        this.repository.close();
        this.git.close();
    }

    @Override
    public File getDotGitDirectory() {
        return repository.getDirectory();
    }

    @Override
    public String abbreviateRef(String ref, int minimumLength) {
        return call(() -> {
            final ObjectId head = git.getRepository().exactRef("HEAD").getObjectId();
            try (ObjectReader reader = git.getRepository().newObjectReader()) {
                if (minimumLength == 0) {
                    return reader.abbreviate(head);
                } else {
                    return reader.abbreviate(head, minimumLength);
                }
            }
        }).name();
    }

    @Override
    public String shortenRef(String ref) {
        return Repository.shortenRefName(ref);
    }

    @Override
    public String getHead() {
        return call(() -> git.getRepository().exactRef("HEAD").getObjectId().name());
    }

    @Nullable
    @Override
    public String getFullBranch() {
        return call(() -> {
            final Ref head = git.getRepository().exactRef("HEAD");
            if (head.isSymbolic()) {
                return head.getTarget().getName();
            }
            return null;
        });
    }

    @Nullable
    @Override
    public String getRemotePushUrl(String remoteName) {
        return call(() -> git.remoteList().call().stream()
                .filter(r -> r.getName().equals("origin"))
                .findFirst()
                .map(RemoteConfig::getPushURIs)
                .orElse(Collections.emptyList())
                .stream()
                .map(Object::toString)
                .findFirst()
                .orElse(null));
    }

    @Override
    public int getRemotesCount() {
        return call(() -> git.remoteList().call().size());
    }

    @Override
    public List<CommitData> getCommits(String latestRev, @Nullable String earliestRev) {
        // List all commits between latest and earliest commits -- including the two ends
        final LogCommand logCommand;
        try {
            // Resolve both commits
            final RevCommit latestCommit;
            RevCommit earliestCommit = null;
            try (RevWalk walk = new RevWalk(git.getRepository())) {
                if (earliestRev != null) {
                    earliestCommit = walk.parseCommit(git.getRepository().resolve(earliestRev));
                }
                latestCommit = walk.parseCommit(git.getRepository().resolve(latestRev));
            }
            logCommand = git.log().add(latestCommit);
            // Exclude all parents of earliest commit
            if (earliestCommit != null) {
                for (RevCommit parent : earliestCommit.getParents()) {
                    logCommand.not(parent);
                }
            }

            // List has order of latest (0) to earliest (list.size())
            final List<CommitData> commits = new ArrayList<>();
            for (RevCommit revCommit : logCommand.call()) {
                String message = revCommit.getFullMessage();
                if (!message.endsWith("\n")) {
                    message += "\n";
                }
                String commitId = revCommit.toObjectId().name();
                String shortCommitId = revCommit.toObjectId().abbreviate(7).name();
                Instant commitTime = Instant.ofEpochSecond(revCommit.getCommitTime());
                commits.add(new CommitData(commitId, shortCommitId, commitTime, message));
            }
            return commits;
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Tag> getTags(boolean includeLightweight) {
        try {
            RefDatabase refDatabase = git.getRepository().getRefDatabase();
            return git.tagList().call()
                    .stream()
                    .map(ref -> {
                        String name = ref.getName();
                        if (!name.startsWith("refs/tags/")) {
                            return null;
                        }
                        name = name.substring("refs/tags/".length());
                        Ref target = ref.getLeaf();
                        try {
                            target = refDatabase.peel(target);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        // annotated tags -> peeled object id should be the commit id
                        ObjectId peeledObjectId = target.getPeeledObjectId();
                        if (peeledObjectId != null) {
                            return new Tag(peeledObjectId.getName(), name);
                        } else if (includeLightweight) {
                            return new Tag(target.getObjectId().getName(), name);
                        } else {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DescribeCall describe() {
        return new DescribeCallImpl();
    }

    class DescribeCallImpl extends AbstractDescribeCall {
        private final DescribeCommand command = git.describe();

        @Override
        public String run() {
            return call(() -> command.setLong(longFormat).setTags(includeLightweightTags).setMatch(matchPatterns.toArray(new String[0])).setTarget(this.target).call());
        }
    }

    private <R> R call(ThrowingCallable<R> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private interface ThrowingCallable<R> {
        R call() throws Exception;
    }
}
