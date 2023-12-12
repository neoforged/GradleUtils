/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.specs

import groovy.transform.CompileStatic
import net.minecraftforge.gdi.annotations.DSLProperty
import net.neoforged.gradleutils.InternalAccessor
import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

@CompileStatic
abstract class VersionSpec {
    @Nested
    @DSLProperty(isConfigurable = true)
    abstract VersionBranchesSpec getBranches()

    @Nested
    @DSLProperty(isConfigurable = true)
    abstract VersionTagsSpec getTags()

    // Prefixed to version, separated with a '-' (hyphen)
    @Input
    @Optional
    @DSLProperty
    abstract Property<String> getVersionPrefix();

    // Specific to NeoForge; sets the version prefix to the Minecraft version and adds exempted branches for the MC version
    void minecraftVersion(String mcVersion) {
        branches.suffixExemptedBranches.addAll(mcVersion, mcVersion + '.0', mcVersion + '.x', InternalAccessor.rsplit(mcVersion, '.', 1)[0] + '.x')
        versionPrefix.set(mcVersion)
    }
}
