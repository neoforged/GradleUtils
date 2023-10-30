/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

import javax.inject.Inject

@CompileStatic
class ChangelogGenerationExtension {
    private final Project project
    private boolean registerAllPublications = true

    @Inject
    ChangelogGenerationExtension(Project project) {
        this.project = project
    }

    void from(final String revision) {
        ChangelogUtils.setupChangelogGeneration(project, revision)
        project.afterEvaluate {
            afterEvaluate(project)
        }
    }

    void disableAutomaticPublicationRegistration() {
        this.registerAllPublications = false
    }

    void publish(final MavenPublication mavenPublication) {
        ChangelogUtils.setupChangelogGenerationForPublishing(project, mavenPublication)
    }

    private void afterEvaluate(final Project project) {
        if (registerAllPublications)
            ChangelogUtils.setupChangelogGenerationOnAllPublishTasks(project)
    }
}
