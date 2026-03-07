rootProject.name = "_latest-tests"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    includeBuild("_plugins/root-dependencies-loader")
    includeBuild("_plugins/build-dependencies")
}

includeBuild("_plugins/root-dependencies-loader")
includeBuild("_plugins/build-dependencies")

include(
    "jvm:dynamic-agent-tests",

    "tests",
    "tests:methods-with-spaces-tests"
)
