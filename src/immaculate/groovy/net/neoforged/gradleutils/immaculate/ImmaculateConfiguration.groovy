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
        // Ending files by a new line is good practice, and avoids "No newline at end of file" comments by Git.
        workflow.trailingNewline()
        // Tabs can render differently, and can accidentally be inserted. Enforce spaces only.
        workflow.noTabs()
        // Reorder imports according to simple lexicographic ordering, and remove unused imports.
        workflow.googleFixImports()
        // Allow disabling the formatter for specific sections. Should be used sparingly.
        workflow.toggleOff.set('spotless:off')
        workflow.toggleOn.set('spotless:on')
        // Most formatting rules are handled by the eclipse formatter config.
        // They are generally chosen to match standard Java style, and eliminate discussions about style.
        workflow.eclipse {
            it.version '3.37.0'
            it.config.set(configPath)
        }

        // Wildcard imports:
        // - cannot be automatically removed if unused
        // - make it harder to see which classes are being used
        // - are often inserted automatically by IDEs, which leads to unnecessary diffs in imports
        // courtesy of diffplug/spotless#240
        // https://github.com/diffplug/spotless/issues/240#issuecomment-385206606
        workflow.custom 'noWildcardImports', { String fileContents ->
            if (fileContents.contains('*;\n')) {
                throw new InvalidUserDataException('No wildcard imports are allowed!')
            }
        }

        // Mixing non-nullable annotations with non-annotated types leads to confusion
        // wrt. nullability of non-annotated types.
        // Annotating all types would be too verbose, so we assume non-nullability by default,
        // and disallow non-null annotations which are then unnecessary.
        workflow.custom 'noNotNull', { String fileContents ->
            if (fileContents.contains('@NotNull') || fileContents.contains('@Nonnull')) {
                throw new InvalidUserDataException('@NotNull and @Nonnull are disallowed.')
            }
        }

        // JetBrains nullability annotations can be used in more contexts,
        // and we also use other JB annotations such as @ApiStatus.
        workflow.custom 'jetbrainsNullable', { String fileContents ->
            fileContents.replace('javax.annotation.Nullable', 'org.jetbrains.annotations.Nullable')
        }
    }
}
