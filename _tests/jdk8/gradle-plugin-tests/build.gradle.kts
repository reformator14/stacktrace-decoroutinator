import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.decoroutinator)
    alias(libs.plugins.bytecode.processor)
}

stacktraceDecoroutinator {
    addJvmRuntimeDependency = false
    useTransformedClassesForCompilation = true
    embeddedDebugProbesConfigurations.include = setOf("runtimeClasspath", "testRuntimeClasspath")
}

repositories {
    mavenCentral()
}

dependencies {
    testCompileOnly(libs.bytecode.processor.intrinsics)

    testImplementation(kotlin("test"))
    testImplementation(libs.decoroutinator.methods.with.spaces.tests)
    testImplementation(project(":empty-module"))
    testImplementation(libs.coroutines.core.build)
    testImplementation(libs.coroutines.debug.build)
}

bytecodeProcessor {
    processors = listOf(
        GetCurrentFileNameProcessor,
    )
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

sourceSets {
    test {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}
