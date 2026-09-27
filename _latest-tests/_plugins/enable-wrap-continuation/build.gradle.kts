plugins {
    alias(libs.plugins.kotlin.jvm) version libs.versions.kotlin.latest
    alias(libs.plugins.gradle.publish) version libs.versions.plugin.gradle.publish
    alias(libs.plugins.root.dependencies.loader)
}

group = "_plugins"

rootDependenciesLoader {
    rootPath = file("../../..")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.asm.utils)
    implementation(decoroutinatorIntrinsics)
    implementation(decoroutinatorProvider)
}

gradlePlugin {
    plugins {
        create("latesttests.plugins.enablewrapcontinuation") {
            id = libs.plugins.enable.wrap.continuation.get().pluginId
            implementationClass = "dev.reformator.stacktracedecoroutinator.latesttests.enablewrapcontinuation.EnableWrapContinuationPlugin"
        }
    }
}
