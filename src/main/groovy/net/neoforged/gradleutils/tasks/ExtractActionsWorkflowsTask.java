/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.tasks;

import groovy.text.GStringTemplateEngine;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Task to extract the GitHub Action workflows from the included template zip file.
 */
public abstract class ExtractActionsWorkflowsTask extends CIConfigExtractionTask {

    public ExtractActionsWorkflowsTask() {
        super(".github-workflows.zip", ".github/workflows");
        setDescription("Creates (or recreates) default GitHub Action workflows");
    }

    /**
     * Extracts the .github-workflows.zip file to our target directory.
     *
     * @param fileZip The teamcity zip file.
     * @param destDir The target directory (generally the project directory), where the .github-workflows.zip file will be extracted.
     */
    @Override
    protected void extractZip(final String fileZip, final File destDir) throws Exception {
        final Map<String, Object> binding = new HashMap<>();
        binding.put("project", getProject());
        binding.put("jdkVersion", determineJDKVersion());
        binding.put("commonGroup", findCommonSubstring(
                getProject().getAllprojects().stream()
                        .filter(project -> !project.getGroup().toString().isEmpty())
                        .map(project -> project.getGroup() + "/" + project.getExtensions().getByType(BasePluginExtension.class).getArchivesName().get() + "/")
                        .map(group -> group.replace('.', '/')) // Replace dots with slashes in the group
                        .collect(Collectors.toList())
        ));
        binding.put("withPRPublishing", withPRPublishing);

        final GStringTemplateEngine engine = new GStringTemplateEngine();
        ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            File newFile = newFile(destDir, zipEntry);
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                // fix for Windows-created archives
                File parent = newFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                FileOutputStream fos = new FileOutputStream(newFile);
                // write file content
                fos.write(engine.createTemplate(new InputStreamReader(zis)).make(binding).toString().getBytes(StandardCharsets.UTF_8));
                fos.close();
            }
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
    }

    private boolean withPRPublishing;

    @Input
    public boolean getWithPRPublishing() {
        return this.withPRPublishing;
    }

    @Option(option = "with-pr-publishing", description = "If the workflow for publishing PRs should be enabled")
    public void setWithPRPublishing(boolean withPRPublishing) {
        this.withPRPublishing = withPRPublishing;
    }
}
