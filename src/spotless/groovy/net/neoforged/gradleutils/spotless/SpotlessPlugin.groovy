/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.spotless

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project

@CompileStatic
class SpotlessPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin('com.diffplug.spotless') {
            project.extensions.create('spotlessUtils', SpotlessUtilsExtension, project)
        }
    }
}
