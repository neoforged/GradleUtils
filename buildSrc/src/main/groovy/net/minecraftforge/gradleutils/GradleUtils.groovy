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

import net.minecraftforge.gradleutils.git.CommandLineGitProvider
import net.minecraftforge.gradleutils.git.GitProvider
import net.minecraftforge.gradleutils.git.JGitProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.authentication.http.BasicAuthentication

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

    static gitInfo(File dir, String... globFilters) {
        GitProvider provider = openGitProvider(dir)
        if (provider == null) {
            return [
                    tag          : '0.0',
                    offset       : '0',
                    hash         : '00000000',
                    branch       : 'master',
                    commit       : '0000000000000000000000',
                    abbreviatedId: '00000000'
            ]
        }

        def tag = provider.describe().longFormat(true).includeLightweightTags(true).matching(globFilters).run()
        def desc = tag?.rsplit('-', 2) ?: ['0.0', '0', '00000000']
        def head = provider.head
        def longBranch = provider.fullBranch

        def ret = [:]
        ret.dir = dir.absolutePath
        ret.tag = desc[0]
        if (ret.tag.startsWith("v") && ret.tag.length() > 1 && ret.tag.charAt(1).digit)
            ret.tag = ret.tag.substring(1)
        ret.offset = desc[1]
        ret.hash = desc[2]
        ret.branch = longBranch != null ? provider.shortenRef(longBranch) : null
        ret.commit = head
        ret.abbreviatedId = provider.abbreviateRef(head, 0)

        // Remove any lingering null values
        ret.removeAll { it.value == null }

        return ret
    }

    /**
     * Get a closure to be passed into {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(groovy.lang.Closure)}
     * in a publishing block.
     *
     * Important the following environment variables must be set for this to work:
     *  - MAVEN_USER: Containing the username to use for authentication
     *  - MAVEN_PASSWORD: Containing the password to use for authentication
     *  - MAVEN_URL_RELEASE: Containing the URL to use for the release repository
     *  - MAVEN_URL_SNAPSHOT: Containing the URL to use for the snapshot repository
     *
     * @param project The project
     * @param defaultFolder The default folder if the required maven information is not currently set
     * @return a closure
     */
    static getPublishingForgeMaven(Project project, File defaultFolder = project.rootProject.file('repo')) {
        return setupSnapshotCompatiblePublishing(project, 'https://maven.neoforged.net/releases', defaultFolder)
    }

    /**
     * Get a closure to be passed into {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(groovy.lang.Closure)}
     * in a publishing block, this closure respects the current project's version, with regards to publishing to a release
     * or snapshot repository.
     *
     * Important the following environment variables must be set for this to work:
     *  - MAVEN_USER: Containing the username to use for authentication
     *  - MAVEN_PASSWORD: Containing the password to use for authentication
     *
     * The following environment variables are optional:
     *  - MAVEN_URL_RELEASE: Containing the URL to use for the release repository
     *  - MAVEN_URL_SNAPSHOT: Containing the URL to use for the snapshot repository
     *
     * If the MAVEN_URL_RELEASE is not set the passed in fallback URL will be used for the release repository.
     * By default this is: https://maven.neoforged.net/releases
     * This is done to preserve backwards compatibility with the old {@link #getPublishingForgeMaven(Project, File)} method.
     *
     * @param project The project
     * @param defaultFolder The default folder if the required maven information is not currently set
     * @return a closure
     */
    static setupSnapshotCompatiblePublishing(Project project, String fallbackPublishingEndpoint = 'https://maven.neoforged.net/releases', File defaultFolder = project.rootProject.file('repo'), File defaultSnapshotFolder = project.rootProject.file('snapshots')) {
        return { MavenArtifactRepository it ->
            name 'forge'
            if (System.env.MAVEN_USER && System.env.MAVEN_PASSWORD) {
                def publishingEndpoint = fallbackPublishingEndpoint
                if (System.env.MAVEN_URL_RELEASE) {
                    publishingEndpoint = System.env.MAVEN_URL_RELEASE
                }

                if (project.version.toString().endsWith("-SNAPSHOT") && System.env.MAVEN_URL_SNAPSHOTS) {
                    url System.env.MAVEN_URL_SNAPSHOTS
                } else {
                    url publishingEndpoint
                }
                authentication {
                    basic(BasicAuthentication)
                }
                credentials {
                    username = System.env.MAVEN_USER
                    password = System.env.MAVEN_PASSWORD
                }
            } else {
                if (project.version.toString().endsWith("-SNAPSHOT")) {
                    url 'file://' + defaultSnapshotFolder.getAbsolutePath()
                } else {
                    url 'file://' + defaultFolder.getAbsolutePath()
                }
            }
        }
    }

    /**
     * Get a closure for the Forge maven to be passed into {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(groovy.lang.Closure)}
     * in a repositories block.
     *
     * @return a closure
     */
    static getForgeMaven() {
        return { MavenArtifactRepository it ->
            name 'forge'
            url 'https://maven.neoforged.net/releases'
        }
    }

    /**
     * Get a closure for the Forge maven to be passed into {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(groovy.lang.Closure)}
     * in a repositories block.
     *
     * @return a closure
     */
    static getForgeReleaseMaven() {
        return { MavenArtifactRepository it ->
            name 'forge-releases'
            url 'https://maven.neoforged.net/releases'
        }
    }

    /**
     * Get a closure for the Forge maven to be passed into {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(groovy.lang.Closure)}
     * in a repositories block.
     *
     * @return a closure
     */
    static getForgeSnapshotMaven() {
        return { MavenArtifactRepository it ->
            name 'forge-snapshots'
            url 'https://maven.neoforged.net/snapshots'
        }
    }

    /**
     *
     */
    private static getFilteredInfo(info, boolean prefix, String filter) {
        if (prefix)
            filter += '**'
        return gitInfo(new File(info.dir as String), filter)
    }

    /**
     * Returns a version in the form {@code $tag.$offset}, e.g. 1.0.5
     *
     * @param info A git info object generated from {@link #gitInfo}
     * @return a version in the form {@code $tag.$offset}, e.g. 1.0.5
     */
    static String getTagOffsetVersion(info) {
        return "${info.tag}.${info.offset}"
    }

    /**
     * Returns a version in the form {@code $tag.$offset}, e.g. 1.0.5.
     * The provided filter is used to filter the retrieved tag.
     *
     * @param info A git info object generated from {@link #gitInfo}
     * @param prefix If true, will treat the filter as a prefix.
     * Defaults to false, which means to treat the filter as a glob pattern.
     * @param filter A non-null string filter used when retrieving the tag
     * @return a version in the form {@code $tag.$offset}, e.g. 1.0.5
     */
    static String getFilteredTagOffsetVersion(info, boolean prefix = false, String filter) {
        return getTagOffsetVersion(getFilteredInfo(info, prefix, filter))
    }

    /**
     * Returns a version in the form {@code $tag.$offset}, optionally with the branch
     * appended if it is not in the defined list of allowed branches
     *
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
     * Returns a version in the form {@code $tag.$offset}, optionally with the branch
     * appended if it is not in the defined list of allowed branches.
     * The provided filter is used to filter the retrieved tag.
     *
     * @param info A git info object generated from {@link #gitInfo}
     * @param prefix If true, will treat the filter as a prefix.
     * Defaults to false, which means to treat the filter as a glob pattern.
     * @param filter A non-null string filter used when retrieving the tag
     * @param allowedBranches A list of allowed branches; the current branch is appended if not in this list
     * @return a version in the form {@code $tag.$offset} or {@code $tag.$offset-$branch}
     */
    static String getFilteredTagOffsetBranchVersion(info, boolean prefix = false, String filter, String... allowedBranches) {
        return getTagOffsetBranchVersion(getFilteredInfo(info, prefix, filter), allowedBranches)
    }

    /**
     * Returns a version in the form {@code $mcVersion-$tag.$offset}, optionally with
     * the branch appended if it is not in the defined list of allowed branches
     *
     * @param info A git info object generated from {@link #gitInfo}
     * @param mcVersion The current minecraft version
     * @param allowedBranches A list of allowed branches; the current branch is appended if not in this list
     * @return a version in the form {@code $mcVersion-$tag.$offset} or {@code $mcVersion-$tag.$offset-$branch}
     */
    static String getMCTagOffsetBranchVersion(info, String mcVersion, String... allowedBranches) {
        if (!allowedBranches || allowedBranches.length == 0)
            allowedBranches = [null, 'master', 'main', 'HEAD', mcVersion, mcVersion + '.0', mcVersion + '.x', mcVersion.rsplit('.', 1)[0] + '.x']
        return "$mcVersion-${getTagOffsetBranchVersion(info, allowedBranches)}"
    }

    /**
     * Returns a version in the form {@code $mcVersion-$tag.$offset}, optionally with
     * the branch appended if it is not in the defined list of allowed branches.
     * The provided filter is used to filter the retrieved tag.
     *
     * @param info A git info object generated from {@link #gitInfo}
     * @param prefix If true, will treat the filter as a prefix.
     * Defaults to false, which means to treat the filter as a glob pattern.
     * @param filter A non-null string filter used when retrieving the tag
     * @param mcVersion The current minecraft version
     * @param allowedBranches A list of allowed branches; the current branch is appended if not in this list
     * @return a version in the form {@code $mcVersion-$tag.$offset} or {@code $mcVersion-$tag.$offset-$branch}
     */
    static String getFilteredMCTagOffsetBranchVersion(info, boolean prefix = false, String filter, String mcVersion, String... allowedBranches) {
        return getMCTagOffsetBranchVersion(getFilteredInfo(info, prefix, filter), mcVersion, allowedBranches)
    }

    /**
     * Builds a project url for a project under the minecraft forge organisation.
     *
     * @param project The name of the project. (As in the project slug on github).
     * @return The github url of the project.
     */
    static String buildProjectUrl(String project) {
        return buildProjectUrl("NeoForged", project)
    }

    /**
     * Builds a project url for a project under the given organisation.
     *
     * @param organisation The name of the org. (As in the org slug on github).
     * @param project The name of the project. (As in the project slug on github).
     * @return The github url of the project.
     */
    static String buildProjectUrl(String organisation, String project) {
        return "https://github.com/$organisation/$project"
    }

    /**
     * Builds the github url from the origin remotes push uri.
     * Processes the URI from three different variants into the URL:
     * 1) If the protocol is http(s) based then ".git" is stripped and returned as url.
     * 2) If the protocol is ssh and does contain authentication information then the
     *    username and password are stripped and the url is returned without the ".git"
     *    ending.
     * 3) If the protocol is ssh and does not contain authentication information then
     *    the protocol is switched to https and the ".git" ending is stripped.
     *
     * @param projectDir THe project directory.
     * @return
     */
    static String buildProjectUrl(final File projectDir) {
        GitProvider provider = openGitProvider(projectDir)
        if (provider == null)
            throw new IllegalArgumentException("Could not find git repository for " + projectDir)

        if (provider.remotesCount == 0)
            throw new IllegalStateException("No remotes found in " + projectDir)

        //Get the origin push url.
        def originUrl = provider.getRemotePushUrl("origin")

        //We do not have a origin url
        if (originUrl == null) {
            return ""
        }

        //Determine the protocol
        if (originUrl.startsWith("ssh")) {
            //If ssh then check for authentication data.
            if (originUrl.contains("@")) {
                //We have authentication data: Strip it.
                return "https://" + originUrl.substring(originUrl.indexOf("@") + 1).replace(".git", "")
            } else {
                //No authentication data: Switch to https.
                return "https://" + originUrl.substring(6).replace(".git", "")
            }
        } else if (originUrl.startsWith("http")) {
            //Standard http protocol: Strip the ".git" ending only.
            return originUrl.replace(".git", "")
        }

        //What other case exists? Just to be sure lets return this.
        return originUrl
    }

    /**
     * Configures CI related tasks for all known platforms.
     *
     * @param project The project to configure them on.
     */
    static void setupCITasks(Project project) {
        //Future proofing.
        //For now we only support the TeamCity environment
        setupTeamCityTasks(project)
    }

    /**
     * Sets up the TeamCity CI tasks.
     *
     * @param project The project to configure it on.
     */
    private static void setupTeamCityTasks(Project project) {
        if (System.env.TEAMCITY_VERSION) {
            //Only setup the CI environment if and only if the environment variables are set.
            def teamCityCITask = project.tasks.register("configureTeamCity") {
                //Print the marker lines into the log which configure the pipeline.
                doLast {
                    project.getLogger().lifecycle("Setting project variables and parameters.")
                    println "##teamcity[buildNumber '${project.version}']"
                    println "##teamcity[setParameter name='env.PUBLISHED_JAVA_ARTIFACT_VERSION' value='${project.version}']"
                }
            }
        }
    }

    static File getGitDirectory(File projectDir) {
        def git = openGitProvider(projectDir)
        if (git == null) {
            new IllegalArgumentException("Could not find git directory in or above: " + projectDir.getAbsolutePath())
        }
        return git.dotGitDirectory
    }

    static GitProvider openGitProvider(File projectDir) {
        return CommandLineGitProvider.create(projectDir) ?: JGitProvider.create(projectDir)
    }
}
