/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.specs

import groovy.transform.CompileStatic
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

@CompileStatic
abstract class VersionTagsSpec {
    {
        appendCommitOffset.convention(true)
        extractLabel.convention(false)
        includeLightweightTags.convention(true)
        stripTagLabel.convention(true)
    }

    // Whether to append the the commit count to the version (pre-labeling)
    abstract Property<Boolean> getAppendCommitOffset()

    // Label, which is appended to version separated with a '-'. Can be overriden by extracted label and custom logic 
    abstract Property<String> getLabel()

    // Whether or not to extract the label from the tag; overrides the label set above, can be overriden by custom logic
    abstract Property<Boolean> getExtractLabel()

    // If this label is present on a tag, clear the label for the version and skip that tag (find the next one)
    abstract Property<String> getCleanMarkerLabel()

    // Whether to strip the label from the tag if present at the end (separated by '-') in the final version
    abstract Property<Boolean> getStripTagLabel()

    // Whether to look for lightweight tags
    abstract Property<Boolean> getIncludeLightweightTags()

    // (Glob) filters to match against the tag
    abstract SetProperty<String> getIncludeFilters()

    // Includes a prefix (a filter which matches anything that has certain characters before it)
    void includePrefix(String prefix) {
        includeFilter(prefix + '**')
    }

    void includeFilter(String filter) {
        includeFilters.add(filter)
    }
}
