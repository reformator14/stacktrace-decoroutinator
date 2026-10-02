plugins {
    alias(libs.plugins.root.dependencies.loader)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.decoroutinator)
}

repositories {
    mavenCentral()
}

kotlin {
    // the default target name, as a typical KMP project (e.g. decoroutinator-kmp-demo) declares it
    jvm()

    sourceSets {
        jvmTest.dependencies {
            implementation(project(":tests:methods-with-spaces-tests"))
            implementation(kotlin("test"))
        }
    }
}

dependencies {
    runtimeOnly("jvmTestRuntimeOnly", decoroutinatorMhInvoker)
}

// Same overrides as jvm:gradle-plugin-tests - only because the runtime has to come from the root jars instead of
// Maven. Which configurations and tasks get transformed stays at the plugin's KMP defaults: that's what's tested.
stacktraceDecoroutinator {
    addJvmRuntimeDependency = false
    useTransformedClassesForCompilation = true
    regularDependencyConfigurations.include = emptySet()
    jvmDependencyConfigurations.include = emptySet()
    embeddedDebugProbesConfigurations.include = setOf("jvmRuntimeClasspath", "jvmTestRuntimeClasspath")
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
