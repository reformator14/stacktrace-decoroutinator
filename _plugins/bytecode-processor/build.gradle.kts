plugins {
    alias(libs.plugins.kotlin.jvm) version libs.versions.kotlin.build apply false
    alias(libs.plugins.gradle.publish) version libs.versions.plugin.gradle.publish apply false
}

allprojects {
    group = "dev.reformator.bytecodeprocessor"
    version = "0.0.1-SNAPSHOT"
}
