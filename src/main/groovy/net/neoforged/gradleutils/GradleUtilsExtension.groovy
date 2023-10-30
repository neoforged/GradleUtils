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
import groovy.transform.PackageScope
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
abstract class GradleUtilsExtension {
    private final Project project
    private final Provider<String> calculatedVersion
    @PackageScope
    final Provider<GitInfoValueSource.GitInfo> rawInfo
    private final Provider<Map<String, String>> gitInfo

    @Inject
    GradleUtilsExtension(Project project, ProjectLayout layout, ProviderFactory providers) {
        this.project = project
        gitRoot.convention(layout.projectDirectory)

        this.calculatedVersion = providers.of(VersionCalculatorValueSource) {
            it.parameters {
                it.workingDirectory.set(gitRoot)
                it.versionConfiguration.set(getVersionConfig())
            }
        }

        this.rawInfo = project.providers.of(GitInfoValueSource) {
            it.parameters {
                it.workingDirectory.set(this.gitRoot)
            }
        }
        this.gitInfo = project.objects.mapProperty(String, String)
                .convention(rawInfo.map { it.gitInfo })
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

    Map<String, String> getGitInfo() {
        return gitInfo.get()
    }

    Action<? extends MavenArtifactRepository> publishingMaven(File defaultFolder = project.rootProject.file('repo')) {
        return GradleUtils.getPublishingForgeMaven(project, defaultFolder)
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    Action<? extends MavenArtifactRepository> maven() {
        return GradleUtils.getForgeMaven()
    }
}
