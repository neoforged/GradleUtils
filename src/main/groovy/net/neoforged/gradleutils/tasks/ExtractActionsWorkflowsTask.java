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
        binding.put("commonGroup", findCommonPrefix(
                getProject().getAllprojects().stream()
                        .filter(project -> !project.getGroup().toString().isEmpty())
                        // Don't crash if a project doesn't have the base plugin
                        .filter(project -> project.getExtensions().findByType(BasePluginExtension.class) != null)
                        .map(project -> project.getGroup() + "/" + project.getExtensions().getByType(BasePluginExtension.class).getArchivesName().get() + "/")
                        .map(group -> group.replace('.', '/')) // Replace dots with slashes in the group
                        .collect(Collectors.toList())
        ));
        binding.put("withPRPublishing", withPRPublishing);
        binding.put("withJCC", withJCC);

        // Use the Implementation-Version attribute to store the git commit
        final String gitCommit = ExtractActionsWorkflowsTask.class.getPackage().getImplementationVersion();

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

                // Write header comment
                if (gitCommit != null) {
                    fos.write(String.format(
                            "# File generated by the GradleUtils `%s` task, avoid modifying it directly\n# The template can be found at https://github.com/neoforged/GradleUtils/blob/%s/src/actionsTemplate/resources/%s\n\n",
                            getName(), gitCommit, zipEntry.getName()
                    ).getBytes(StandardCharsets.UTF_8));
                }

                // write file content
                fos.write(engine.createTemplate(new InputStreamReader(zis)).make(binding).toString().getBytes(StandardCharsets.UTF_8));
                fos.close();
            }
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
    }

    private boolean withPRPublishing, withJCC;

    @Input
    public boolean getWithPRPublishing() {
        return this.withPRPublishing;
    }

    @Input
    public boolean getWithJCC() {
        return this.withJCC;
    }

    @Option(option = "pr-publishing", description = "If the workflow for publishing PRs should be enabled")
    public void setWithPRPublishing(boolean withPRPublishing) {
        this.withPRPublishing = withPRPublishing;
    }

    @Option(option = "jcc", description = "If the workflow for JarCompatibilityChecker should be enabled")
    public void setWithJCC(boolean withJCC) {
        this.withJCC = withJCC;
    }
}
