// Slim settings file used by the analyst Docker build.
// Includes only :analyst and its :pipelineCore dependency — skips :composeApp, :shared,
// :server, :scripts, :agent so Gradle doesn't configure Android/iOS toolchains unavailable in
// the container. Both the TYPESAFE_PROJECT_ACCESSORS feature preview and :pipelineCore are
// required because :analyst depends on projects.pipelineCore; omitting either breaks the image
// build (see MS-390 for the same mistake on the orchestrator).
rootProject.name = "MediaSage"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":analyst")
include(":pipelineCore")
