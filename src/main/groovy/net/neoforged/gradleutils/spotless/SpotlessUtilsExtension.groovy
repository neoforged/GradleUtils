/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.spotless

import com.diffplug.gradle.spotless.JavaExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessTask
import groovy.transform.CompileStatic
import org.gradle.api.Project

import javax.inject.Inject
import java.nio.file.Files

@CompileStatic
abstract class SpotlessUtilsExtension {
    final File configPath

    @Inject
    SpotlessUtilsExtension(Project project) {
        configPath = new File(project.rootDir, '.gradle/formatter-config.xml')

        if (!configPath.exists()) {
            Files.createDirectories(configPath.toPath().parent)

            try (
                final input = SpotlessUtilsExtension.getResourceAsStream('/formatter-config.xml');
                final output = Files.newOutputStream(configPath.toPath())
            ) {
                byte[] buffer = new byte[1024];
                int len
                while ((len = input.read(buffer)) > 0) {
                    output.write(buffer, 0, len)
                }
            }
        }

        final extract = project.tasks.register('extraGUSpotlessConfiguration', ExtractSpotlessConfiguration) {
            it.output.set(configPath)
            it.targetConfiguration.set(SpotlessUtilsExtension.getResource('/formatter-config.xml'))
        }

        project.tasks.withType(SpotlessTask).configureEach {
            it.dependsOn(extract)
        }
    }

    void configure(SpotlessExtension ext) {
        ext.java(this.&configure)
    }

    void configure(JavaExtension ext) {
        ext.endWithNewline()
        ext.indentWithSpaces()
        ext.removeUnusedImports()
        ext.toggleOffOn()
        ext.eclipse().configFile(configPath)
        ext.importOrder()
    }
}
