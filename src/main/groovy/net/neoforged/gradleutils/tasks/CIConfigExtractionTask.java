/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.zip.ZipEntry;

public abstract class CIConfigExtractionTask extends DefaultTask {
    private final String templateZipName, targetDirName;
    protected CIConfigExtractionTask(String templateZipName, String targetDirName) {
        this.templateZipName = templateZipName;
        this.targetDirName = targetDirName;
        getDestination().convention(getProject().getRootProject().getLayout().getProjectDirectory().dir(getProject().provider(() -> "./")));

        setGroup("publishing");
    }

    @TaskAction
    public void run() throws Exception {
        //Get the destination directory (by default the current root project directory)
        final File destDir = getDestination().getAsFile().get();
        //Grab the target directory, to check if it exists.
        final File targetDir = new File(destDir, targetDirName);

        //Export the zip file from our resources.
        String fileZip = exportResource();

        //Check if the directory exists, if so then delete it.
        if (targetDir.exists() && !disableDeletion) {
            boolean couldDelete = true;
            for (File file : targetDir.listFiles()) {
                couldDelete &= file.delete();
            }

            //Try to delete it.
            if (!(couldDelete && targetDir.delete())) {
                //Something went really wrong....
                throw new IllegalStateException("Could not delete the existing " + targetDirName + " project directory!");
            }
        }

        //Extract the zip from our runtime file.
        extractZip(fileZip, destDir);
    }

    protected abstract void extractZip(String fileZip, File destDir) throws Exception;

    @InputDirectory
    @PathSensitive(PathSensitivity.NONE)
    public abstract DirectoryProperty getDestination();

    /**
     * Creates a new file or directory.
     *
     * @param destinationDir The target directory.
     * @param zipEntry       The entry in the .teamcity.zip file.
     * @return The new file or directory.
     */
    protected static File newFile(File destinationDir, ZipEntry zipEntry) throws Exception {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    protected String determineJDKVersion() {
        if (getProject().getExtensions().findByType(JavaPluginExtension.class) == null) {
            getProject().getLogger().warn("Could not find the Java extension, falling back to JDK 8.");
            return "8";
        }

        return getProject().getExtensions().getByType(JavaPluginExtension.class)
                .getToolchain().getLanguageVersion()
                .orElse(getProject().provider(() -> JavaLanguageVersion.of(8)))
                .get().toString();
    }

    private String exportResource() throws Exception {
        InputStream stream = null;
        OutputStream resStreamOut = null;
        String jarFolder;
        try {
            stream = CIConfigExtractionTask.class.getResourceAsStream("/" + templateZipName);//note that each / is a directory down in the "jar tree" been the jar the root of the tree
            if (stream == null) {
                throw new Exception("Cannot get resource \"" + templateZipName + "\" from Jar file.");
            }

            int readBytes;
            byte[] buffer = new byte[4096];
            jarFolder = new File(CIConfigExtractionTask.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParentFile().getPath().replace('\\', '/');
            resStreamOut = new FileOutputStream(jarFolder + templateZipName);
            while ((readBytes = stream.read(buffer)) > 0) {
                resStreamOut.write(buffer, 0, readBytes);
            }
        } finally {
            if (stream != null) {
                stream.close();
            }
            if (resStreamOut != null) {
                resStreamOut.close();
            }
        }

        return jarFolder + templateZipName;
    }

    private boolean disableDeletion;

    @Input
    public boolean getDisableDeletion() {
        return this.disableDeletion;
    }

    @Option(option = "disable-deletion", description = "Disable deletion of existing workflows")
    public void setDisableDeletion(boolean disableDeletion) {
        this.disableDeletion = disableDeletion;
    }

    /**
     * Finds the most common 0-based substring of all {@code groups}.
     */
    protected static String findCommonSubstring(List<String> groups) {
        if (groups.isEmpty()) {
            return "";
        }

        final Deque<Character> firstGroup = new ArrayDeque<>();
        groups.get(0).chars().forEach(ch -> firstGroup.add(Character.valueOf((char) ch)));

        String common = "";
        while (!firstGroup.isEmpty()) {
            final String current = common + firstGroup.pop();
            if (groups.stream().allMatch(group -> group.startsWith(current))) {
                common = current;
            } else {
                break;
            }
        }

        return common;
    }
}
