/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

//file:noinspection UnstableApiUsage
package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.transform.PackageScope
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

import javax.annotation.Nullable

@PackageScope
@CompileStatic
interface GitInfoValueSource extends ValueSource<GitInfo, Parameters> {
    interface Parameters extends ValueSourceParameters {
        DirectoryProperty getWorkingDirectory()

        SetProperty<String> getTagFilters()
    }

    @Override
    default GitInfo obtain() {
        try (Repository repo = new FileRepositoryBuilder().findGitDir(parameters.workingDirectory.get().asFile).build()) {
            final git = Git.wrap(repo)

            final filters = parameters.tagFilters.get().<String> toArray(new String[0])
            final tag = git.describe().setLong(true).setTags(true).setMatch(filters).call()
            final desc = GradleUtils.rsplit(tag, '-', 2) ?: ['0.0', '0', '00000000']
            final head = git.repository.exactRef('HEAD')
            final String longBranch = head.symbolic ? head?.target?.name : null
            // matches Repository.getFullBranch() but returning null when on a detached HEAD

            Map<String, String> gitInfoMap = [:]
            gitInfoMap.dir = repo.getDirectory().parentFile.absolutePath
            gitInfoMap.tag = desc[0]
            if (gitInfoMap.tag.startsWith("v") && gitInfoMap.tag.length() > 1 && gitInfoMap.tag.charAt(1).digit)
                gitInfoMap.tag = gitInfoMap.tag.substring(1)
            gitInfoMap.offset = desc[1]
            gitInfoMap.hash = desc[2]
            gitInfoMap.branch = longBranch != null ? Repository.shortenRefName(longBranch) : null
            gitInfoMap.commit = ObjectId.toString(head.objectId)
            gitInfoMap.abbreviatedId = head.objectId.abbreviate(8).name()

            // Remove any lingering null values
            gitInfoMap.removeAll { it.value == null }

            final originUrl = getRemotePushUrl(git, "origin")

            return new GitInfo(gitInfoMap, originUrl)

        } catch (RepositoryNotFoundException ignored) {
            return new GitInfo([
                    tag          : '0.0',
                    offset       : '0',
                    hash         : '00000000',
                    branch       : 'master',
                    commit       : '0000000000000000000000',
                    abbreviatedId: '00000000'
            ], null)
        }
    }

    @Nullable
    default String getRemotePushUrl(Git git, String remoteName) {
        def remotes = git.remoteList().call()
        if (remotes.size() == 0)
            return null

        // Get the origin remote
        def originRemote = remotes.toList().stream()
                .filter(r -> r.getName() == remoteName)
                .findFirst()
                .orElse(null)

        //We do not have an origin named remote
        if (originRemote == null) return null

        // Get the origin push url.
        def originUrl = originRemote.getURIs().toList().stream()
                .findFirst()
                .orElse(null)

        if (originUrl == null) return null // No origin URL

        return transformPushUrl(originUrl.toString())
    }
    
    default String transformPushUrl(String url) {
        if (url.startsWith("ssh")) {
            // Convert SSH urls to HTTPS
            // Check for authentication data (e.g., username:password@example.com)
            if (url.contains("@")) {
                // Strip authentication data
                return "https://" + url.substring(url.indexOf("@") + 1).replace(".git", "")
            } else {
                // 'ssh://' is 6 characters
                return "https://" + url.substring(6).replace(".git", "")
            }
        } else if (url.startsWith("http")) {
            // Already in HTTP(S), so strip the ".git" ending
            return url.replace(".git", "")
        }

        // Some other protocol? We don't handle this, so just return the same
        return url
    }

    @Immutable
    class GitInfo {
        final Map<String, String> gitInfo
        @Nullable
        final String originUrl
    }
}