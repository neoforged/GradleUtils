/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.immaculate

import dev.lukebemish.immaculate.CheckTask
import dev.lukebemish.immaculate.FormattingWorkflow
import dev.lukebemish.immaculate.ImmaculateExtension
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project

/**
 * This is a separate class to avoid class-loading issues when immaculate is *not* applied to the project.
 */
final class ImmaculateConfiguration {
    private ImmaculateConfiguration() {
    }

    static void apply(Project project) {
        final configPath = new File(project.rootDir, '.gradle/formatter-config.xml')

        final extract = project.tasks.register('extractGUImmaculateConfiguration', ExtractImmaculateConfiguration) {
            it.output.set(configPath)
        }

        project.tasks.withType(CheckTask).configureEach {
            it.dependsOn(extract)
        }

        var immaculate = (ImmaculateExtension) project.extensions.getByName("immaculate")

        immaculate.workflows.register("java") {
            configure(it, configPath)
        }
    }

    static void configure(FormattingWorkflow workflow, File configPath) {
        workflow.java()
        workflow.trailingNewline()
        workflow.noTabs()
        workflow.googleFixImports()
        workflow.toggleOff.set('spotless:off')
        workflow.toggleOn.set('spotless:on')
        workflow.eclipse {
            it.version '3.37.0'
            it.config.set(configPath)
        }

        // courtesy of diffplug/spotless#240
        // https://github.com/diffplug/spotless/issues/240#issuecomment-385206606
        workflow.custom 'noWildcardImports', { String fileContents ->
            if (fileContents.contains('*;\n')) {
                throw new InvalidUserDataException('No wildcard imports are allowed!')
            }
        }

        workflow.custom 'noNotNull', { String fileContents ->
            if (fileContents.contains('@NotNull') || fileContents.contains('@Nonnull')) {
                throw new InvalidUserDataException('@NotNull and @Nonnull are disallowed.')
            }
        }

        workflow.custom 'jetbrainsNullable', { String fileContents ->
            fileContents.replace('javax.annotation.Nullable', 'org.jetbrains.annotations.Nullable')
        }
    }
}
