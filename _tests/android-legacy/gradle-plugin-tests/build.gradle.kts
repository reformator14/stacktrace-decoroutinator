import com.android.build.gradle.internal.tasks.R8Task
import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor
import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.decoroutinator)
    alias(libs.plugins.bytecode.processor)
}

stacktraceDecoroutinator {
    useTransformedClassesForCompilation = true
    embedDebugProbesForAndroid = true
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    compileOnly(libs.bytecode.processor.intrinsics)

    implementation(libs.decoroutinator.tests)
    implementation(project(":empty-module"))
    implementation(libs.coroutines.core.build)
    implementation(libs.junit4)
    implementation(libs.jupiter.api)
    implementation(libs.decoroutinator.aar)
    implementation(libs.androidx.test.rules)
    implementation(libs.coroutines.debug.build)

    runtimeOnly(libs.decoroutinator.mh.invoker.jvm)
    runtimeOnly(libs.decoroutinator.generator.jvm)
}

val mappingFileDevicePath = "/sdcard/decoroutinator/tests/android-legacy/gradle-plugin-mapping.txt"

bytecodeProcessor {
    processors = listOf(
        GetCurrentFileNameProcessor,
        LoadConstantProcessor,
        GetOwnerClassProcessor
    )
    initContext {
        LoadConstantProcessor.addValues(
            context = this,
            valuesByKeys = mapOf("mappingFile" to mappingFileDevicePath)
        )
    }
}

afterEvaluate {
    val minifyDebugWithR8Task = tasks.named<R8Task>("minifyDebugWithR8")

    val pushMinifyDebugMappingFileTask = tasks.register<Exec>("pushMinifyDebugMappingFile") {

        commandLine(
            androidComponents.sdkComponents.adb.get().asFile.absolutePath,
            "push",
            minifyDebugWithR8Task.get().mappingFile.get().asFile.absolutePath,
            mappingFileDevicePath
        )
        dependsOn(minifyDebugWithR8Task)
    }

    tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
        dependsOn(pushMinifyDebugMappingFileTask)
    }
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.tests.androidlegacy.gradleplugintests"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    defaultConfig {
        applicationId = "dev.reformator.stacktracedecoroutinator.tests.androidlegacy.gradleplugintests"
        versionCode = 1
        versionName = "1.0"
        minSdk = 16
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
    buildTypes {
        debug {
            isMinifyEnabled = true
            isDebuggable = false
            testProguardFiles(decoroutinatorAndroidProGuardRules(), "proguard-rules.pro")
            proguardFiles(decoroutinatorAndroidProGuardRules(), "proguard-rules.pro")
        }
    }
}

tasks.register("legacyAndroidTest") {
    dependsOn("connectedAndroidTest")
}
