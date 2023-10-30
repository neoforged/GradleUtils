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

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import net.neoforged.gradleutils.specs.VersionSpec
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Nested

import javax.inject.Inject

@CompileStatic
abstract class NewGradleUtilsExtension {
    private final Project project
    private final Provider<String> calculatedVersion

    @Inject
    NewGradleUtilsExtension(Project project, ProjectLayout layout, ProviderFactory providers) {
        this.project = project
        gitRoot.convention(layout.projectDirectory)
        calculatedVersion = providers.of(VersionCalculatorValueSource) {
            it.parameters {
                it.workingDirectory.set(gitRoot)
                it.versionConfiguration.set(getVersionConfig())
            }
        }
    }

    abstract DirectoryProperty getGitRoot()

    @Nested
    abstract VersionSpec getVersionConfig()

    Object getVersion() {
        // This allows lazily calculating the version, only when its needed (someone `toString`s this object)
        return new Object() {
            @Override
            String toString() {
                return calculatedVersion.get()
            }
        }
    }

    void version(Action<? extends VersionSpec> configureAction) {
        configureAction.execute(versionConfig)
    }

    Action<? extends MavenArtifactRepository> publishingMaven(File defaultFolder = project.rootProject.file('repo')) {
        return GradleUtils.getPublishingForgeMaven(project, defaultFolder)
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    Action<? extends MavenArtifactRepository> maven() {
        return GradleUtils.getForgeMaven()
    }
}
