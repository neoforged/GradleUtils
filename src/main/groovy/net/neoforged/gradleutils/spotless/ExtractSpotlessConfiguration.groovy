/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.spotless

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.file.Paths

@CompileStatic
@DisableCachingByDefault(because = 'configuration contained inside the jar may change')
abstract class ExtractSpotlessConfiguration extends DefaultTask {
    @Input
    abstract Property<URL> getTargetConfiguration()

    @OutputFile
    abstract RegularFileProperty getOutput()

    ExtractSpotlessConfiguration() {
        // configuration contained inside the jar may change
        outputs.upToDateWhen {
            false
        }
    }

    @TaskAction
    void run() {
        final output = this.getOutput().get().asFile.toPath()
        if (Files.notExists(output)) {
            Files.createDirectories(output.parent)
        }

        try (
            final input = SpotlessUtilsExtension.getResourceAsStream('/formatter-config.xml');
            final out = Files.newOutputStream(output)
        ) {
            byte[] buffer = new byte[1024]
            int len
            while ((len = input.read(buffer)) > 0) {
                out.write(buffer, 0, len)
            }
        }
    }
}
