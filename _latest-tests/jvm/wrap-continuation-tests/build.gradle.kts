import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor
import dev.reformator.stacktracedecoroutinator.latesttests.enablewrapcontinuation.enabledWrapContinuationAttribute

plugins {
    alias(libs.plugins.root.dependencies.loader)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.bytecode.processor)
    alias(libs.plugins.enable.wrap.continuation)
}

buildscript {
    dependencies {
        classpath("_plugins:build-dependencies")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testCompileOnly(bytecodeProcessorIntrinsics)

    testImplementation(decoroutinatorJvm)
    testImplementation(project(":tests:methods-with-spaces-tests"))
    testImplementation(libs.junit5.platform.launcher)
    testImplementation(libs.jupiter.api)
    testImplementation(libs.coroutines.core.latest)
    testImplementation(kotlin("test"))
}

bytecodeProcessor {
    processors = listOf(
        GetCurrentFileNameProcessor,
        GetOwnerClassProcessor
    )
}

afterEvaluate {
    configurations.testRuntimeClasspath.get().attributes.attribute(enabledWrapContinuationAttribute, true)
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    test {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}
