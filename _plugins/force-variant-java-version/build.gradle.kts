import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) version libs.versions.kotlin.build
    alias(libs.plugins.gradle.publish) version libs.versions.plugin.gradle.publish
}

group = "dev.reformator.forcevariantjavaversion"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}


gradlePlugin {
    plugins {
        create("forcevariantjavaversionPlugin") {
            id = libs.plugins.force.variant.java.version.get().pluginId
            implementationClass = "dev.reformator.forcevariantjavaversion.ForceVariantJavaVersionPlugin"
        }
    }
}
