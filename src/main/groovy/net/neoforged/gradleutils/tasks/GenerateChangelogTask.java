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

import net.neoforged.gradleutils.GradleUtilsExtension;
import net.neoforged.gradleutils.InternalAccessor;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public abstract class GenerateChangelogTask extends DefaultTask
{

    public GenerateChangelogTask()
    {
        final GradleUtilsExtension extension = getProject().getExtensions().getByType(GradleUtilsExtension.class);

        //Setup defaults: Using merge-base based text changelog generation of the local project into build/changelog.txt
        getWorkingDirectory().fileValue(getProject().getProjectDir());
        getBuildMarkdown().convention(false);
        getOutputFile().convention(getProject().getLayout().getBuildDirectory().file("changelog.txt"));
        getStartingCommit().convention("");
        getStartingTag().convention("");
        getProjectUrl().convention(InternalAccessor.getOriginUrl(extension));
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.NONE)
    public abstract DirectoryProperty getWorkingDirectory();

    @Input
    public abstract Property<Boolean> getBuildMarkdown();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<String> getStartingCommit();

    @Input
    public abstract Property<String> getStartingTag();

    @Input
    public abstract Property<String> getProjectUrl();

    @TaskAction
    public void generate() {
        final String startingCommit = getStartingCommit().getOrElse("");
        final String startingTag = getStartingTag().getOrElse("");

        if (!startingCommit.isEmpty() && !startingTag.isEmpty()) {
            throw new IllegalStateException("Both starting commit and tag are supplied to the task: " + getName() + ". Only supply one!");
        }

        String changelog = "";
        if (startingCommit.isEmpty() && startingTag.isEmpty()) {
            changelog = InternalAccessor.generateChangelog(getWorkingDirectory().getAsFile().get(), getProjectUrl().get(), !getBuildMarkdown().get());
        }
        else if (startingCommit.isEmpty())  {
            changelog = InternalAccessor.generateChangelog(getWorkingDirectory().getAsFile().get(), getProjectUrl().get(), !getBuildMarkdown().get(), startingTag);
        }
        else {
            changelog = InternalAccessor.generateChangelogFromCommit(getWorkingDirectory().getAsFile().get(), getProjectUrl().get(), !getBuildMarkdown().get(), startingCommit);
        }

        final File outputFile = getOutputFile().getAsFile().get();
        outputFile.getParentFile().mkdirs();
        if (outputFile.exists()) {
            outputFile.delete();
        }

        try
        {
            Files.write(outputFile.toPath(), changelog.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to write changelog to file: " + outputFile.getAbsolutePath());
        }
    }
}
