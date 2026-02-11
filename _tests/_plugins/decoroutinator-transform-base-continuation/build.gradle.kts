import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm).version(libs.versions.kotlin.build)
    alias(libs.plugins.gradle.publish).version(libs.versions.plugin.gradle.publish)
}

group = "dev.reformator.decoroutinatortransformbasecontinuation"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.decoroutinator.clazz.transformer)
    implementation(libs.decoroutinator.intrinsics)
    implementation(libs.asm.utils)
}

gradlePlugin {
    plugins {
        create("decoroutinatorTransformBaseContinuation") {
            id = libs.plugins.decoroutinator.transform.base.continuation.get().pluginId
            implementationClass = "dev.reformator.decoroutinatortransformbasecontinuation.DecoroutinatorTransformBaseContinuationPlugin"
        }
    }
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
