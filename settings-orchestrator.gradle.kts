// Slim settings file used by the orchestrator Docker build.
// Includes only :orchestrator and its :pipelineCore dependency — skips :composeApp, :shared,
// :appServer, :scripts so Gradle doesn't configure Android/iOS toolchains unavailable in the
// container. :pipelineCore is required because :orchestrator depends on projects.pipelineCore
// (MS-381); omitting it breaks the orchestrator image build (MS-390).
rootProject.name = "MediaSage"
// Required for the `projects.pipelineCore` type-safe accessor used in orchestrator/build.gradle.kts.
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

include(":orchestrator")
include(":pipelineCore")
