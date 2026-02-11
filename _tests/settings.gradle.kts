rootProject.name = "_tests"

includeBuild("_plugins/decoroutinator-transform-base-continuation")
includeBuild("..")
includeBuild("../_plugins/bytecode-processor")
includeBuild("../_plugins/force-variant-java-version")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }

    includeBuild("_plugins/decoroutinator-transform-base-continuation")
    includeBuild("..")
    includeBuild("../_plugins/bytecode-processor")
    includeBuild("../_plugins/force-variant-java-version")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}



include(
    "android:wrong-generator-tests",
    "android:gradle-plugin-tests",
    "jdk8:generator-jvm-tests",
    "jdk8:gradle-plugin-tests",
    "jdk8:wrong-generator-tests",
    "jdk8:mh-invoker-jvm-tests",
    "jvm:generator-jvm-tests",
    "jvm:gradle-plugin-tests",
    "jvm:mh-invoker-jvm-tests",
    "android-legacy:gradle-plugin-tests",
    "gradle-plugin-groovy-dsl-tests",

    "empty-module"
)
