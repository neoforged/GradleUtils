import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.projectFeatures.githubIssues

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2021.2"

project {

    buildType(Build)
    buildType(PullRequests_1)

    params {
        param("git_main_branch", "main")
        param("github_repository_name", "GradleUtils")
    }

    features {
        githubIssues {
            id = "PROJECT_EXT_4"
            displayName = "MinecraftForge/GradleUtils"
            repositoryURL = "https://github.com/MinecraftForge/GradleUtils"
        }
    }
}

object Build : BuildType({
    templates(AbsoluteId("MinecraftForge_BuildWithDiscordNotifications"), AbsoluteId("MinecraftForge_GradleBuild"), AbsoluteId("MinecraftForge_BuildMainBranches"))
    name = "Build"

    steps {
        step {
            name = "Build"
            id = "RUNNER_2"
            type = "MinecraftForge_ExecuteGradleTask"
            executionMode = BuildStep.ExecutionMode.DEFAULT
            param("gradle_tasks", "%gradle_build_task%")
            param("additional_gradle_parameters", "--refresh-dependencies --continue -x %gradle_test_task%")
        }
    }
})

object PullRequests_1 : BuildType({
    templates(AbsoluteId("MinecraftForge_BuildWithDiscordNotifications"), AbsoluteId("MinecraftForge_BuildPullRequests"), AbsoluteId("MinecraftForge_GradleBuild"))
    id("PullRequests")
    name = "Pull Requests"
    description = "Builds pull requests for the project"
})
