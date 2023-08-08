/*
 * GradleUtils
 * Copyright (C) 2021 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradleutils.git;

import org.eclipse.jgit.api.DescribeCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.transport.RemoteConfig;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;

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

    JGitProvider(Repository repository) {
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
    public DescribeCall describe() {
        return new DescribeCallImpl();
    }

    class DescribeCallImpl extends AbstractDescribeCall {
        private final DescribeCommand command = git.describe();

        @Override
        public String run() {
            return call(() -> command.setLong(longFormat).setTags(includeLightweightTags).setMatch(matchPatterns).call());
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
