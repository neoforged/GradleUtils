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

package net.minecraftforge.gradleutils

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository

class GradleUtils {
    static {
        String.metaClass.rsplit = { String del, int limit = -1 ->
            def lst = new ArrayList()
            def x = 0, idx
            def tmp = delegate
            while ((idx = tmp.lastIndexOf(del)) != -1 && (limit == -1 || x++ < limit)) {
                lst.add(0, tmp.substring(idx + del.length(), tmp.length()))
                tmp = tmp.substring(0, idx)
            }
            lst.add(0, tmp)
            return lst
        }
    }

    static gitInfo(File dir) {
        def git
        try {
            git = Git.open(dir)
        } catch (RepositoryNotFoundException e) {
            return [
                    tag: '0.0',
                    offset: '0',
                    hash: '00000000',
                    branch: 'master',
                    commit: '0000000000000000000000',
                    abbreviatedId: '00000000'
            ]
        }
        def tag = git.describe().setLong(true).setTags(true).call()
        def desc = tag?.rsplit('-', 2) ?: ['0.0', '0', '00000000']
        def head = git.repository.exactRef('HEAD')
        def longBranch = head.symbolic ? head?.target?.name : null // matches Repository.getFullBranch() but returning null when on a detached HEAD

        def ret = [:]
        ret.tag = desc[0]
        ret.offset = desc[1]
        ret.hash = desc[2]
        ret.branch = longBranch != null ? Repository.shortenRefName(longBranch) : null
        ret.commit = ObjectId.toString(head.objectId)
        ret.abbreviatedId = head.objectId.abbreviate(8).name()

        return ret
    }

    /**
     * Returns a version in the form {@code $tag.$offset}, e.g. 1.0.5
     * @param info A git info object generated from {@link #gitInfo}
     * @return a version in the form {@code $tag.$offset}, e.g. 1.0.5
     */
    static String getTagOffsetVersion(info) {
        return "${info.tag}.${info.offset}"
    }

    /**
     * Returns a version in the form {@code $tag.$offset}, optionally with the branch
     * appended if it is not in the defined list of allowed branches
     * @param info A git info object generated from {@link #gitInfo}
     * @param allowedBranches A list of allowed branches; the current branch is appended if not in this list
     * @return a version in the form {@code $tag.$offset} or {@code $tag.$offset-$branch}
     */
    static String getTagOffsetBranchVersion(info, String... allowedBranches) {
        if (!allowedBranches || allowedBranches.length == 0)
            allowedBranches = [null, 'master', 'main', 'HEAD']
        def version = getTagOffsetVersion(info)
        String branch = info.branch
        if (branch?.startsWith('pulls/'))
            branch = 'pr' + branch.rsplit('/', 1)[1]
        branch = branch?.replaceAll(/[\\\/]/, '-')
        return branch in allowedBranches ? version : "$version-${branch}"
    }

    /**
     * Returns a version in the form {@code $mcVersion-$tag.$offset}, optionally with
     * the branch appended if it is not in the defined list of allowed branches
     * @param mcVersion The current minecraft version
     * @param info A git info object generated from {@link #gitInfo}
     * @param allowedBranches A list of allowed branches; the current branch is appended if not in this list
     * @return a version in the form {@code $mcVersion-$tag.$offset} or {@code $mcVersion-$tag.$offset-$branch}
     */
    static String getMCTagOffsetBranchVersion(info, String mcVersion, String... allowedBranches) {
        if (!allowedBranches || allowedBranches.length == 0)
            allowedBranches = [null, 'master', 'main', 'HEAD', mcVersion, mcVersion + '.0', mcVersion + '.x', mcVersion.rsplit('.', 1)[0] + '.x']
        return "$mcVersion-${getTagOffsetBranchVersion(info, allowedBranches)}"
    }
}
