/*
 * GradleUtils
 * Copyright (C) 2021 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.neoforged.gradleutils.tasks;

import net.neoforged.gradleutils.InternalAccessor;
import net.neoforged.gradleutils.GradleUtilsExtension;
import net.neoforged.gradleutils.specs.VersionSpec;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public abstract class GenerateChangelogTask extends DefaultTask {

    public GenerateChangelogTask() {
        final GradleUtilsExtension extension = getProject().getExtensions().getByType(GradleUtilsExtension.class);
        getVersionConfig().convention(extension.getVersionConfig());
        getOutputFile().convention(getProject().getLayout().getBuildDirectory().file("changelog.txt"));
    }

    @Inject
    protected abstract ProjectLayout getLayout();

    @Inject
    protected abstract ProviderFactory getProviders();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<String> getStartingRevision();

    @Input
    public abstract Property<VersionSpec> getVersionConfig();

    @TaskAction
    public void generate() {
        final String startingRev = getStartingRevision().get();

        final String changelog = InternalAccessor.generateChangelog(getProviders(), getVersionConfig().get(),
                getLayout().getProjectDirectory(), startingRev);

        final File outputFile = getOutputFile().getAsFile().get();
        if (outputFile.getParentFile() != null) {
            //noinspection ResultOfMethodCallIgnored
            outputFile.getParentFile().mkdirs();
        }
        if (outputFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outputFile.delete();
        }

        try {
            Files.write(outputFile.toPath(), changelog.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write changelog to file: " + outputFile.getAbsolutePath());
        }
    }
}
