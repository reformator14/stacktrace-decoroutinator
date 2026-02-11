rootProject.name = "bytecode-processor"
includeBuild("../force-variant-java-version")

pluginManagement {
    includeBuild("../force-variant-java-version")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

include(
    "api",
    "plugins",
    "intrinsics",
    "gradle-plugin"
)
project(":api").name = "bytecode-processor-api"
project(":plugins").name = "bytecode-processor-plugins"
project(":intrinsics").name = "bytecode-processor-intrinsics"
project(":gradle-plugin").name = "bytecode-processor-gradle-plugin"