/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.gradleutils.git;

import net.neoforged.gradleutils.git.GitProvider.DescribeCall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Abstract skeleton of {@link DescribeCall}, for de-duplicating common code.
 */
abstract class AbstractDescribeCall implements GitProvider.DescribeCall {
    protected boolean longFormat = false;
    protected boolean includeLightweightTags = false;
    protected List<String> matchPatterns = new ArrayList<>();

    protected AbstractDescribeCall() {
    }

    @Override
    public DescribeCall longFormat(boolean longFormat) {
        this.longFormat = longFormat;
        return this;
    }

    @Override
    public DescribeCall includeLightweightTags(boolean lightweight) {
        this.includeLightweightTags = lightweight;
        return this;
    }

    @Override
    public DescribeCall matching(String... patterns) {
        if (patterns.length == 0) {
            this.matchPatterns.clear();
        } else {
            this.matchPatterns.addAll(Arrays.asList(patterns));
        }
        return this;
    }
}
