// Slim settings file used by the agent Docker build.
// Includes only :agent and its :pipeline-core dependency — skips :composeApp, :shared,
// :server, :scripts so Gradle doesn't configure Android/iOS toolchains unavailable in the
// container. :pipeline-core is required because :agent depends on projects.pipelineCore (MS-381);
// omitting it breaks the orchestrator image build (MS-390).
rootProject.name = "MediaSage"
// Required for the `projects.pipelineCore` type-safe accessor used in agent/build.gradle.kts.
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

include(":agent")
include(":pipeline-core")
