/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import net.neoforged.gradleutils.git.GitProvider
import net.neoforged.gradleutils.specs.VersionSpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

@CompileStatic
@PackageScope
abstract class VersionCalculatorValueSource implements ValueSource<String, Parameters> {
    static interface Parameters extends ValueSourceParameters {
        DirectoryProperty getWorkingDirectory()

        Property<VersionSpec> getVersionConfiguration()
    }

    @Override
    String obtain() {
        final calculator = new VersionCalculator(parameters.versionConfiguration.get())

        try (GitProvider provider = GradleUtils.openGitProvider(parameters.workingDirectory.get().asFile)) {
            return calculator.calculate(provider)
        }
    }
}
