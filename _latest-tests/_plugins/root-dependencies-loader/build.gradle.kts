plugins {
    alias(libs.plugins.kotlin.jvm) version libs.versions.kotlin.latest
    alias(libs.plugins.gradle.publish) version libs.versions.plugin.gradle.publish
}

group = "_plugins"

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("latesttests.plugins.rootdependenciesloader") {
            id = libs.plugins.root.dependencies.loader.get().pluginId
            implementationClass = "dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.RootDependenciesLoaderPlugin"
        }
    }
}
