/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import org.gradle.api.provider.Provider

/**
 * <strong>Internal</strong> accessor for methods in this package that needs to be accessed <strong>only</strong> 
 * from subpackages. <strong>Do not use this class from outside GradleUtils</strong>, as this class may be changed or
 * removed at any time. (In other words, this class is not part of the public API.)
 */
@CompileStatic
class InternalAccessor {
    static String generateChangelog(final File projectDirectory, final String repositoryUrl, final boolean justText) {
        return ChangelogUtils.generateChangelog(projectDirectory, repositoryUrl, justText)
    }

    static String generateChangelog(final File projectDirectory, final String repositoryUrl, final boolean justText, final String sourceTag) {
        return ChangelogUtils.generateChangelog(projectDirectory, repositoryUrl, justText, sourceTag)
    }

    static String generateChangelogFromCommit(final File projectDirectory, final String repositoryUrl, final boolean justText, final String commitHash) {
        return ChangelogUtils.generateChangelog(projectDirectory, repositoryUrl, justText, commitHash)
    }

    static Provider<String> getOriginUrl(final GradleUtilsExtension extension) {
        return extension.rawInfo.map { it.originUrl }
    }
}
