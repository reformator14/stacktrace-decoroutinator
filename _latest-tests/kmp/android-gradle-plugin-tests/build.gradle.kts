plugins {
    alias(libs.plugins.root.dependencies.loader)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.decoroutinator)
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    android {
        namespace = "dev.reformator.stacktracedecoroutinator.latesttests.kmp.androidgradleplugintests"
        compileSdk = libs.versions.android.compile.sdk.get().toInt()
        minSdk = 26
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        packaging {
            resources.pickFirsts.add("META-INF/*")
            resources.excludes.add("win32-x86-64/attach_hotspot_windows.dll")
            resources.excludes.add("win32-x86/attach_hotspot_windows.dll")
            resources.excludes.add("META-INF/licenses/*")
        }
    }

    sourceSets {
        getByName("androidDeviceTest").dependencies {
            implementation(project(":tests"))
        }
    }
}

dependencies {
    runtimeOnly("androidDeviceTestRuntimeOnly", decoroutinatorMhInvoker)
    "androidDeviceTestRuntimeOnly"(libs.androidx.test.runner)
}

// Same overrides as android:gradle-plugin-tests - only because the runtime has to come from the root jars instead of
// Maven. Which configurations and tasks get transformed stays at the plugin's KMP defaults: that's what's tested.
stacktraceDecoroutinator {
    addJvmRuntimeDependency = false
    addAndroidRuntimeDependency = false
    useTransformedClassesForCompilation = true
    embedDebugProbesForAndroid = true
    regularDependencyConfigurations.include = emptySet()
    androidDependencyConfigurations.include = emptySet()
    jvmDependencyConfigurations.include = emptySet()
}
