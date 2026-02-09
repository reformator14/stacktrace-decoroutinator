import com.android.build.gradle.internal.tasks.R8Task
import com.android.build.gradle.internal.tasks.factory.dependsOn
import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor
import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import org.gradle.kotlin.dsl.named

plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    id("dev.reformator.stacktracedecoroutinator")
    id("dev.reformator.bytecodeprocessor")
}

repositories {
    mavenCentral()
    google()
}

stacktraceDecoroutinator {
    regularDependencyConfigurations.include = emptySet()
    androidDependencyConfigurations.include = emptySet()
    jvmDependencyConfigurations.include = emptySet()
    addJvmRuntimeDependency = false
    addAndroidRuntimeDependency = false
    useTransformedClassesForCompilation = true
    embedDebugProbesForAndroid = true
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.gradlepluginandroidlegacytests"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.reformator.stacktracedecoroutinator.gradlepluginandroidlegacytests"
        versionCode = 1
        versionName = "1.0"
        minSdk = 14
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }
    packaging {
        resources.pickFirsts.add("META-INF/*")
        resources.excludes.add("win32-x86-64/attach_hotspot_windows.dll")
        resources.excludes.add("win32-x86/attach_hotspot_windows.dll")
        resources.excludes.add("META-INF/licenses/*")
    }
    kotlin {
        jvmToolchain(8)
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

dependencies {
    implementation(project(":test-utils"))
    implementation(libs.multidex)
    implementation(project(":stacktrace-decoroutinator-common"))
    implementation(libs.junit4)
    implementation(libs.kotlinx.coroutines.jdk8.build)
    implementation(libs.junit5.api)
    implementation(libs.kotlinx.coroutines.debug.build)
    implementation(libs.androidx.annotation)

    runtimeOnly(project(":stacktrace-decoroutinator-runtime-settings"))
    runtimeOnly(project(":stacktrace-decoroutinator-generator-android"))
    runtimeOnly(project(":stacktrace-decoroutinator-mh-invoker-android"))
    runtimeOnly(project(":stacktrace-decoroutinator-mh-invoker-jvm"))
    runtimeOnly(project(":stacktrace-decoroutinator-generator-jvm"))
    runtimeOnly(libs.androidx.test.runner)

    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
}

val minifyDebugMappingFileDevicePath = "/sdcard/debugMappingLegacy.txt"

bytecodeProcessor {
    processors = listOf(
        LoadConstantProcessor,
        GetOwnerClassProcessor,
        GetCurrentFileNameProcessor
    )
    initContext {
        LoadConstantProcessor.addValues(
            context = this,
            valuesByKeys = mapOf("minifyDebugMappingFile" to minifyDebugMappingFileDevicePath)
        )
    }
}

afterEvaluate {
    val minifyDebugWithR8Task = tasks.named<R8Task>("minifyDebugWithR8")

    val pushMinifyDebugMappingFileTask = tasks.register<Exec>("pushMinifyDebugMappingFile") {
        commandLine(
            "${android.sdkDirectory}/platform-tools/adb",
            "push",
            minifyDebugWithR8Task.get().mappingFile.get().asFile.absolutePath,
            minifyDebugMappingFileDevicePath
        )
        dependsOn(minifyDebugWithR8Task)
    }

    tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
        dependsOn(pushMinifyDebugMappingFileTask)
    }
}

afterEvaluate {
    tasks.register("legacyAndroidTest").dependsOn(tasks.named("connectedAndroidTest"))
}
