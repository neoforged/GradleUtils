/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import net.neoforged.gradleutils.specs.VersionSpec
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

@CompileStatic
@PackageScope
abstract class ChangelogGeneratorValueSource implements ValueSource<String, Parameters> {
    static interface Parameters extends ValueSourceParameters {
        DirectoryProperty getWorkingDirectory()

        Property<VersionSpec> getVersionSpec()

        Property<String> getEarliestRevision()
    }

    @Override
    String obtain() {
        final calculator = new VersionCalculator(parameters.versionSpec.get())
        final generator = new ChangelogGenerator(calculator)

        try (Repository repo = new FileRepositoryBuilder().findGitDir(parameters.workingDirectory.get().asFile).build()) {
            final git = Git.wrap(repo)

            return generator.generate(git, parameters.earliestRevision.get())
        }
    }
}
