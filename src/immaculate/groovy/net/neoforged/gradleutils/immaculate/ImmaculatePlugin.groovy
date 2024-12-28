/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.immaculate


import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project

@CompileStatic
class ImmaculatePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin('dev.lukebemish.immaculate') {
            ImmaculateConfiguration.apply(project)
        }
    }
}
