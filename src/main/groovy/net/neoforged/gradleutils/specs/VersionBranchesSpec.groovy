/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.specs

import groovy.transform.CompileStatic
import net.minecraftforge.gdi.annotations.DSLProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input

@CompileStatic
abstract class VersionBranchesSpec {
    {
        suffixBranch.convention(false)
        suffixExemptedBranches.convention(DEFAULT_ALLOWED_BRANCHES).addAll(DEFAULT_ALLOWED_BRANCHES)

        suffixExemptedBranchPatterns.convention([])
    }

    private static final Collection<String> DEFAULT_ALLOWED_BRANCHES = Arrays.asList('', 'main', 'master', 'HEAD')

    // Whether to suffix the branch to the version, separated with a '-' (hyphen), 
    // Only suffixed if suffixExemptedBranches does not contains the branch
    @Input
    @DSLProperty
    abstract Property<Boolean> getSuffixBranch();

    /**
     * Branch names which are exempted from being suffixed (see suffixBranch above)
     * Empty string means a situation where the branch cannot be named, for some reason (detached HEAD?)
     */
    @Input
    @DSLProperty
    abstract SetProperty<String> getSuffixExemptedBranches()

    /**
     * Branch name patterns which are exempted from being suffixed (see suffixBranch above)
     * Empty string means a situation where the branch cannot be named, for some reason (detached HEAD?)
     */
    @Input
    @DSLProperty
    abstract SetProperty<String> getSuffixExemptedBranchPatterns()
}
