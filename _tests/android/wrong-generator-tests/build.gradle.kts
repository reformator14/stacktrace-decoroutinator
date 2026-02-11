import dev.reformator.decoroutinatortransformbasecontinuation.decoroutinatorTransformedBaseContinuationAttribute
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.decoroutinator.transform.base.continuation)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    androidTestImplementation(libs.decoroutinator.common)
    androidTestImplementation(libs.coroutines.core.build)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.jupiter.api)

    androidTestRuntimeOnly(libs.decoroutinator.mh.invoker)
    androidTestRuntimeOnly(libs.decoroutinator.generator.jvm)
    androidTestRuntimeOnly(libs.decoroutinator.naive.base.continuation.accessor)
    androidTestRuntimeOnly(libs.androidx.test.runner)
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.tests.android.wronggeneratortests"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources.pickFirsts.add("META-INF/*")
        resources.excludes.add("win32-x86-64/attach_hotspot_windows.dll")
        resources.excludes.add("win32-x86/attach_hotspot_windows.dll")
        resources.excludes.add("META-INF/licenses/*")
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
    }
}

afterEvaluate {
    configurations["debugAndroidTestRuntimeClasspath"].attributes
        .attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
}
