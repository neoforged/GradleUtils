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
import groovy.transform.NamedVariant
import groovy.transform.PackageScope
import io.github.gradlenexus.publishplugin.NexusPublishExtension
import io.github.gradlenexus.publishplugin.NexusPublishPlugin
import net.neoforged.gradleutils.specs.VersionSpec
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

import javax.inject.Inject

@CompileStatic
abstract class GradleUtilsExtension {
    private transient Project project
    private final Directory rootProjectDir
    private final Provider<String> projectVersion
    private final Provider<String> calculatedVersion
    @PackageScope
    final Provider<GitInfoValueSource.GitInfo> rawInfo
    private final Provider<Map<String, String>> gitInfo

    @Inject
    GradleUtilsExtension(Project project, ProjectLayout layout, ObjectFactory objects, ProviderFactory providers) {
        this.project = project
        this.projectVersion = project.provider { project.version.toString() }
        this.rootProjectDir = project.rootProject.layout.projectDirectory
        gitRoot.convention(layout.projectDirectory)

        this.calculatedVersion = providers.of(VersionCalculatorValueSource) {
            it.parameters {
                it.workingDirectory.set(gitRoot)
                it.versionConfiguration.set(getVersionSpec())
            }
        }

        this.rawInfo = providers.of(GitInfoValueSource) {
            it.parameters {
                it.workingDirectory.set(this.gitRoot)
            }
        }
        this.gitInfo = objects.mapProperty(String, String)
                .convention(rawInfo.map { it.gitInfo })

        final possibleSecring = providers.gradleProperty('signing.secretKeyRingFile')
        shouldSign.convention(project.provider { (System.getenv('GPG_PRIVATE_KEY') || System.getenv('GPG_SUBKEY')) || possibleSecring.getOrNull() })
    }

    abstract DirectoryProperty getGitRoot()

    @Nested
    abstract VersionSpec getVersionSpec()

    @Input
    abstract Property<Boolean> getShouldSign()

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
        configureAction.execute(versionSpec)
    }

    Map<String, String> getGitInfo() {
        return gitInfo.get()
    }

    Action<? extends MavenArtifactRepository> getPublishingMaven(File defaultFolder = rootProjectDir.file('repo').asFile) {
        return GradleUtils.setupSnapshotCompatiblePublishing(projectVersion, 'https://maven.neoforged.net/releases', 
                defaultFolder, rootProjectDir.file('snapshot').asFile)
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    Action<? extends MavenArtifactRepository> getMaven() {
        return GradleUtils.getForgeMaven()
    }

    void setupCentralPublishing() {
        if (project.rootProject !== project) {
            throw new UnsupportedOperationException('The nexus publishing plugin can only be applied on the root project!')
        }

        project.plugins.apply(NexusPublishPlugin)
        project.extensions.configure(NexusPublishExtension) { ext ->
            ext.repositories { container ->
                container.sonatype {
                    it.username.set(System.getenv('SONATYPE_USER') ?: '')
                    it.password.set(System.getenv('SONATYPE_PASSWORD') ?: '')
                    it.nexusUrl.set(URI.create('https://s01.oss.sonatype.org/service/local/'))
                }
            }
        }
    }

    void sign(Publication publication, Project project = this.project) {
        ifSigning({
            it.sign(publication)
        }, project)
    }

    void signAllPublications(PublicationContainer container, Project project = this.project) {
        ifSigning({
            it.sign(container)
        }, project)
    }

    private void ifSigning(Action<SigningExtension> action, Project project = this.project) {
        project.afterEvaluate {
            if (shouldSign.get()) {
                project.extensions.configure(SigningExtension) { ext ->
                    action.execute(ext)
                }
            }
        }
    }

    @NamedVariant
    void setupSigning(Project project = this.project, boolean signAllPublications = false) {
        project.plugins.apply(SigningPlugin)
        project.afterEvaluate {
            if (shouldSign.get()) {
                project.extensions.configure(SigningExtension) { signing ->
                    var signingKey = System.getenv('GPG_PRIVATE_KEY') ?: ''
                    var signingPassword = System.getenv('GPG_KEY_PASSWORD') ?: ''
                    if (signingKey && signingPassword) {
                        signing.useInMemoryPgpKeys(signingKey, signingPassword)
                    } else {
                        signingKey = System.getenv('GPG_SUBKEY') ?: ''
                        signingPassword = System.getenv('GPG_SUBKEY_PASSWORD') ?: ''
                        final keyId = System.getenv('GPG_SUBKEY_ID') ?: ''
                        if (keyId && signingKey && signingPassword) {
                            signing.useInMemoryPgpKeys(keyId, signingKey, signingPassword)
                        }
                    }
                }
            }
        }

        if (signAllPublications) {
            this.signAllPublications(project.extensions.getByType(PublishingExtension).publications)
        }
    }
}
