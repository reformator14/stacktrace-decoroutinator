rootProject.name = "build-dependencies"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../../gradle/libs.versions.toml"))
        }
    }
}

pluginManagement {
    includeBuild("../root-dependencies-loader")
}

includeBuild("../root-dependencies-loader")
