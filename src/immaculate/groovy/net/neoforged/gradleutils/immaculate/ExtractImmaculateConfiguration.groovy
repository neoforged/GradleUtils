/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.immaculate

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files

@CompileStatic
@DisableCachingByDefault(because = 'configuration contained inside the jar may change')
abstract class ExtractImmaculateConfiguration extends DefaultTask {
    @OutputFile
    abstract RegularFileProperty getOutput()

    ExtractImmaculateConfiguration() {
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
                final input = ExtractImmaculateConfiguration.getResourceAsStream('/formatter-config.xml');
                final out = Files.newOutputStream(output)
        ) {
            input.transferTo(out)
        }
    }
}
