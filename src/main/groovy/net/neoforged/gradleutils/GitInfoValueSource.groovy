/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

//file:noinspection UnstableApiUsage
package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.transform.PackageScope
import net.neoforged.gradleutils.git.GitProvider
import org.eclipse.jgit.lib.Repository
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.SetProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.slf4j.Logger

import javax.annotation.Nullable

@PackageScope
@CompileStatic
abstract class GitInfoValueSource implements ValueSource<GitInfo, Parameters> {
    private static final Logger LOGGER = Logging.getLogger(GitInfoValueSource.class)
    
    static interface Parameters extends ValueSourceParameters {
        DirectoryProperty getWorkingDirectory()

        SetProperty<String> getTagFilters()
    }

    @Override
    GitInfo obtain() {
        try (GitProvider provider = GradleUtils.openGitProvider(parameters.workingDirectory.get().asFile)) {
            final filters = parameters.tagFilters.get().<String> toArray(new String[0])
            final tag = provider.describe().longFormat(true).includeLightweightTags(true).matching(filters).run()
            final desc = GradleUtils.rsplit(tag, '-', 2) ?: ['0.0', '0', '00000000']
            final head = provider.head
            final String longBranch = provider.fullBranch
            // matches Repository.getFullBranch() but returning null when on a detached HEAD

            Map<String, String> gitInfoMap = [:]
            gitInfoMap.dir = provider.dotGitDirectory.parentFile.absolutePath
            gitInfoMap.tag = desc[0]
            if (gitInfoMap.tag.startsWith("v") && gitInfoMap.tag.length() > 1 && gitInfoMap.tag.charAt(1).digit)
                gitInfoMap.tag = gitInfoMap.tag.substring(1)
            gitInfoMap.offset = desc[1]
            gitInfoMap.hash = desc[2]
            gitInfoMap.branch = longBranch != null ? Repository.shortenRefName(longBranch) : null
            gitInfoMap.commit = head // TODO: double-check this is a commit
            gitInfoMap.abbreviatedId = provider.abbreviateRef(head, 8)

            // Remove any lingering null values
            gitInfoMap.removeAll { it.value == null }

            final originUrl = transformPushUrl(provider.getRemotePushUrl("origin"))

            return new GitInfo(gitInfoMap, originUrl)

        } catch (Exception ex) {
            LOGGER.warn("Failed to obtain git info", ex)
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

    private static String transformPushUrl(String url) {
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
    static class GitInfo {
        final Map<String, String> gitInfo
        @Nullable
        final String originUrl
    }
}