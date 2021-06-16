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

    static gitInfo(dir) {
        def git = null
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
        def desc = tag?.rsplit('-', 2) ?: '0.0'
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

    static String getSimpleVersion(info) {
        return "${info.tag}.${info.offset}"
    }
}
