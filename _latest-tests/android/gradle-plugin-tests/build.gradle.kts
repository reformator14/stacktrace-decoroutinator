plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.decoroutinator)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    androidTestImplementation(project(":tests"))

    androidTestRuntimeOnly(libs.androidx.test.runner)
}

stacktraceDecoroutinator {
    addJvmRuntimeDependency = false
    addAndroidRuntimeDependency = false
    useTransformedClassesForCompilation = true
    embedDebugProbesForAndroid = true
    regularDependencyConfigurations.include = emptySet()
    androidDependencyConfigurations.include = emptySet()
    jvmDependencyConfigurations.include = emptySet()
    embeddedDebugProbesConfigurations.include = setOf("runtimeClasspath", "testRuntimeClasspath")
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.latesttests.android.gradleplugintests"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    defaultConfig {
        applicationId = "dev.reformator.stacktracedecoroutinator.latesttests.android.gradleplugintests"
        versionCode = 1
        versionName = "1.0"
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources.pickFirsts.add("META-INF/*")
        resources.excludes.add("win32-x86-64/attach_hotspot_windows.dll")
        resources.excludes.add("win32-x86/attach_hotspot_windows.dll")
        resources.excludes.add("META-INF/licenses/*")
    }
}
