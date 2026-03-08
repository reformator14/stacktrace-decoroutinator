plugins {
    alias(libs.plugins.root.dependencies.loader)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.decoroutinator)
}

repositories {
    mavenCentral()
}

dependencies {
    testRuntimeOnly(decoroutinatorMhInvoker)

    testImplementation(project(":tests:methods-with-spaces-tests"))
    testImplementation(kotlin("test"))
}

stacktraceDecoroutinator {
    addJvmRuntimeDependency = false
    useTransformedClassesForCompilation = true
    regularDependencyConfigurations.include = emptySet()
    jvmDependencyConfigurations.include = emptySet()
    embeddedDebugProbesConfigurations.include = setOf("runtimeClasspath", "testRuntimeClasspath")
}

sourceSets {
    test {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}

tasks.test {
    useJUnitPlatform()
}
