/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

import javax.inject.Inject

// For some reason, annotating this as @CompileStatic causes the Groovy compiler to crash with StackOverflowError
// TODO: investigate what in this class causes the Groovy compiler to crash when annotated with @CompileStatic
class ChangelogGenerationExtension {
    private final Project project
    private boolean registerAllPublications = true

    @Inject
    ChangelogGenerationExtension(Project project) {
        this.project = project
    }

    void fromMergeBase() {
        ChangelogUtils.setupChangelogGeneration(project)
        project.afterEvaluate(this::afterEvaluate)
    }

    void fromTag(final String tag) {
        ChangelogUtils.setupChangelogGenerationFromTag(project, tag)
        project.afterEvaluate(this::afterEvaluate)
    }

    void fromCommit(final String commit) {
        ChangelogUtils.setupChangelogGenerationFromCommit(project, commit)
        project.afterEvaluate(this::afterEvaluate)
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
