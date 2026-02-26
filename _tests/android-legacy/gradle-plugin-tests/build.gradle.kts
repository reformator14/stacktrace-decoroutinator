import com.android.build.gradle.internal.tasks.R8Task
import com.android.build.gradle.internal.tasks.factory.dependsOn
import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor
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
    implementation(libs.coroutines.debug.build)
    implementation(libs.androidx.test.monitor)

    runtimeOnly(libs.decoroutinator.mh.invoker.jvm)
    runtimeOnly(libs.decoroutinator.generator.jvm)
    runtimeOnly(libs.androidx.test.runner)
}

val copyMappingFileToAssetsTask = tasks.register<Copy>("copyMappingFileToAssets") {
    into(layout.buildDirectory.dir("generated/mapping-assets"))
    rename { "mapping.txt" }
}


bytecodeProcessor {
    processors = listOf(
        GetCurrentFileNameProcessor,
        GetOwnerClassProcessor
    )
}

afterEvaluate {
    copyMappingFileToAssetsTask.configure {
        from(tasks.named<R8Task>("minifyDebugWithR8").flatMap { it.mappingFile })
    }
    tasks.named("mergeDebugAssets").dependsOn(copyMappingFileToAssetsTask)
    tasks.named("generateDebugLintVitalReportModel").dependsOn(copyMappingFileToAssetsTask)
    tasks.named("lintVitalAnalyzeDebug").dependsOn(copyMappingFileToAssetsTask)
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.tests.androidlegacy.gradleplugintests"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    defaultConfig {
        applicationId = "dev.reformator.stacktracedecoroutinator.tests.androidlegacy.gradleplugintests"
        versionCode = 1
        versionName = "1.0"
        minSdk = 14
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
    sourceSets["debug"].assets.srcDir(copyMappingFileToAssetsTask.map { it.destinationDir })
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
