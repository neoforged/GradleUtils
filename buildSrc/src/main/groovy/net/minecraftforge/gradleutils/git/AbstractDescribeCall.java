/*
 * GradleUtils
 * Copyright (C) 2021 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradleutils.git;

import net.minecraftforge.gradleutils.git.GitProvider.DescribeCall;

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
