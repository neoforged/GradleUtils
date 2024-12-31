/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import net.neoforged.gradleutils.specs.VersionSpec
import org.gradle.api.file.Directory
import org.gradle.api.provider.ProviderFactory

import javax.annotation.Nullable

/**
 * <strong>Internal</strong> accessor for methods in this package that needs to be accessed <strong>only</strong> 
 * from subpackages. <strong>Do not use this class from outside GradleUtils</strong>, as this class may be changed or
 * removed at any time. (In other words, this class is not part of the public API.)
 */
@CompileStatic
class InternalAccessor {
    static List<String> rsplit(@Nullable String input, String del, int limit = -1) {
        return GradleUtils.rsplit(input, del, limit)
    }

    static String generateChangelog(ProviderFactory providers, VersionSpec versionConfig, Directory workingDirectory,
                                    @Nullable String earliestRevision) {
        final changelog = providers.of(ChangelogGeneratorValueSource) {
            it.parameters {
                it.workingDirectory.set(workingDirectory)
                it.versionSpec.set(versionConfig)
                it.earliestRevision.set(earliestRevision)
            }
        }
        return changelog.get()
    }
}
