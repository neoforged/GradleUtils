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

package net.neoforged.gradleutils

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import net.neoforged.gradleutils.git.CommandLineGitProvider
import net.neoforged.gradleutils.git.GitProvider
import net.neoforged.gradleutils.git.JGitProvider
import org.eclipse.jgit.api.Git
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.authentication.http.BasicAuthentication

import javax.annotation.Nullable

@CompileStatic
@PackageScope
class GradleUtils {

    static List<String> rsplit(@Nullable String input, String del, int limit = -1) {
        if (input == null) return null
        List<String> lst = []
        int x = 0, idx
        String tmp = input
        while ((idx = tmp.lastIndexOf(del)) != -1 && (limit == -1 || x++ < limit)) {
            lst.add(0, tmp.substring(idx + del.length(), tmp.length()))
            tmp = tmp.substring(0, idx)
        }
        lst.add(0, tmp)
        return lst
    }

    static Map<String, String> gitInfo(File dir, String... globFilters) {
        GitProvider provider = openGitProvider(dir)
        if (provider == null) {
            return [
                    tag: '0.0',
                    offset: '0',
                    hash: '00000000',
                    branch: 'master',
                    commit: '0000000000000000000000',
                    abbreviatedId: '00000000'
            ]
        }

        def tag = provider.describe().longFormat(true).includeLightweightTags(true).matching(globFilters).run()
        def desc = rsplit(tag, '-', 2) ?: ['0.0', '0', '00000000']
        def head = provider.head
        def longBranch = provider.fullBranch

        Map<String, String> ret = [:]
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
     *
     * @param project The project
     * @param defaultFolder The default folder if the required maven information is not currently set
     * @return a closure
     */
    static Action<? extends MavenArtifactRepository> setupSnapshotCompatiblePublishing(Provider<String> projectVersion, String fallbackPublishingEndpoint = 'https://maven.neoforged.net/releases', File defaultFolder, File defaultSnapshotFolder) {
        return { MavenArtifactRepository it ->
            it.name = 'forge'
            String version = projectVersion.get()
            if (System.getenv('MAVEN_USER') && System.getenv('MAVEN_PASSWORD')) {
                def publishingEndpoint = fallbackPublishingEndpoint
                if (System.getenv('MAVEN_URL_RELEASE')) {
                    publishingEndpoint = System.getenv('MAVEN_URL_RELEASE')
                }

                if (version.endsWith("-SNAPSHOT") && System.getenv('MAVEN_URL_SNAPSHOTS')) {
                    it.url = System.getenv('MAVEN_URL_SNAPSHOTS')
                } else {
                    it.url = publishingEndpoint
                }
                it.authentication {
                    it.create('basic', BasicAuthentication)
                }
                it.credentials { credentials ->
                    credentials.username = System.getenv('MAVEN_USER')
                    credentials.password = System.getenv('MAVEN_PASSWORD')
                }
            } else {
                if (version.endsWith("-SNAPSHOT")) {
                    it.url = 'file://' + defaultSnapshotFolder.getAbsolutePath()
                } else {
                    it.url = 'file://' + defaultFolder.getAbsolutePath()
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
    static Action<? extends MavenArtifactRepository> getForgeMaven() {
        return { MavenArtifactRepository it ->
            it.name = 'forge'
            it.url = 'https://maven.neoforged.net/releases'
        }
    }

    /**
     * Returns a version in the form {@code $tag.$offset}, e.g. 1.0.5
     *
     * @param info A git info object generated from {@link #gitInfo}
     * @return a version in the form {@code $tag.$offset}, e.g. 1.0.5
     */
    static String getTagOffsetVersion(Map<String, String> info) {
        return "${info.tag}.${info.offset}"
    }

    /**
     * Returns a version in the form {@code $tag.$offset}, optionally with the branch
     * appended if it is not in the defined list of allowed branches
     *
     * @param info A git info object generated from {@link #gitInfo}
     * @param allowedBranches A list of allowed branches; the current branch is appended if not in this list
     * @return a version in the form {@code $tag.$offset} or {@code $tag.$offset-$branch}
     */
    static String getTagOffsetBranchVersion(Map<String, String> info, String... allowedBranches) {
        if (!allowedBranches || allowedBranches.length == 0)
            allowedBranches = [null, 'master', 'main', 'HEAD']
        def version = getTagOffsetVersion(info)
        String branch = info.branch
        if (branch?.startsWith('pulls/'))
            branch = 'pr' + rsplit(branch, '/', 1)[1]
        branch = branch?.replaceAll(/[\\\/]/, '-')
        return branch in allowedBranches ? version : "$version-${branch}"
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
    static String getMCTagOffsetBranchVersion(Map<String, String> info, String mcVersion, String... allowedBranches) {
        if (!allowedBranches || allowedBranches.length == 0)
            allowedBranches = [null, 'master', 'main', 'HEAD', mcVersion, mcVersion + '.0', mcVersion + '.x', rsplit(mcVersion, '.', 1)[0] + '.x']
        return "$mcVersion-${getTagOffsetBranchVersion(info, allowedBranches)}"
    }

    /**
     * Configures CI related tasks for all known platforms.
     *
     * @param project The project to configure them on.
     */
    static void setupCITasks(Project project) {
        setupTeamCityTasks(project)
        setupActionsTasks(project)
    }

    /**
     * Sets up the TeamCity CI tasks.
     *
     * @param project The project to configure it on.
     */
    private static void setupTeamCityTasks(Project project) {
        if (System.getenv('TEAMCITY_VERSION')) {
            // Only setup the CI environment if and only if the environment variables are set.
            final versionProvider = project.provider { project.version?.toString() }
            project.tasks.register("configureTeamCity", ConfigureTeamCity) {
                it.version.set(versionProvider)
            }
        }
    }

    /**
     * Sets up the GitHub Action CI tasks.
     *
     * @param project the project to configure it on
     */
    private static void setupActionsTasks(Project project) {
        if (System.getenv('GITHUB_ACTION')) {
            // Only setup the CI environment if and only if the environment variables are set.
            final versionProvider = project.provider { project.version?.toString() }
            project.tasks.register("configureGitHubActions", ConfigureActions) {
                it.version.set(versionProvider)
            }
        }
    }

    @UntrackedTask(because = 'CI configuration should always run in every build')
    abstract static class ConfigureTeamCity extends DefaultTask {
        @Input
        abstract Property<String> getVersion()

        @TaskAction
        void doAction() {
            final versionString = version.get()
            // Print marker lines into the log which configure the pipeline
            logger.lifecycle("Setting project variables and parameters.")
            println "##teamcity[buildNumber '${versionString}']"
            println "##teamcity[setParameter name='env.PUBLISHED_JAVA_ARTIFACT_VERSION' value='${versionString}']"
        }
    }

    @UntrackedTask(because = 'CI configuration should always run in every build')
    abstract static class ConfigureActions extends DefaultTask {
        @Input
        abstract Property<String> getVersion()

        @TaskAction
        void doAction() {
            final versionString = version.get()
            // Print marker lines into the log which configure the pipeline
            logger.lifecycle("Setting project variables and parameters.")
            new File(System.getenv('GITHUB_OUTPUT')) << "version=$versionString"
        }
    }

    static Git openGit(File projectDir, Throwable lastException = null) {
        try {
            return Git.open(projectDir)
        } catch (IOException e) {
            if (projectDir.getParentFile() != null) {
                return openGit(projectDir.getParentFile(), lastException == null ? e : lastException)
            } else {
                throw lastException
            }
        }
    }

    static GitProvider openGitProvider(File projectDir) {
        return CommandLineGitProvider.create(projectDir) ?: JGitProvider.create(projectDir)
    }
}
