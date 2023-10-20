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

    void fromMergeBase() {
        ChangelogUtils.setupChangelogGeneration(project)
        // These shouldn't be project.afterEvaluate(this::afterEvaluate), otherwise the Groovy compiler crashes
        // Don't know why, but it's just how it is
        project.afterEvaluate {
            afterEvaluate(project)
        }
    }

    void fromTag(final String tag) {
        ChangelogUtils.setupChangelogGenerationFromTag(project, tag)
        project.afterEvaluate {
            afterEvaluate(project)
        }
    }

    void fromCommit(final String commit) {
        ChangelogUtils.setupChangelogGenerationFromCommit(project, commit)
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
