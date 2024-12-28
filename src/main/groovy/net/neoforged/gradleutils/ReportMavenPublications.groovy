/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.annotations.NotNull

/**
 * Implements reporting of published Maven publications at the end of the build.
 */
@CompileStatic
abstract class ReportMavenPublications implements BuildService<Params>, AutoCloseable {
    private final List<Publication> artifacts = Collections.synchronizedList(new ArrayList<Publication>())

    void record(String groupId, String artifactId, String version) {
        artifacts.add(new Publication(
                groupId,
                artifactId,
                version
        ))
    }

    private boolean isEnabled() {
        return getParameters().getEnabled().getOrElse(false);
    }

    abstract static class Params implements BuildServiceParameters {
        abstract Property<Boolean> getEnabled();
    }

    @Override
    void close() throws Exception {
        if (!isEnabled() || artifacts.isEmpty()) {
            return
        }

        def sortedArtifacts = new ArrayList<>(artifacts)
        sortedArtifacts.sort()

        println()
        println("=" * 80)
        println(" PUBLISHED MAVEN PUBLICATIONS")
        println("=" * 80)
        println()
        for (final def artifact in sortedArtifacts) {
            println("\t$artifact.groupId:$artifact.artifactId:$artifact.version")
        }
        println()
    }

    private static final class Publication implements Comparable<Publication> {
        private final String groupId;
        private final String artifactId;
        private final String version;

        Publication(String groupId, String artifactId, String version) {
            this.groupId = groupId
            this.artifactId = artifactId
            this.version = version
        }

        @Override
        int compareTo(@NotNull Publication o) {
            return groupId <=> o.groupId ?: artifactId <=> o.artifactId ?: version <=> o.version
        }
    }
}
