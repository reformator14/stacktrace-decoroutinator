import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.gradleplugin.BytecodeProcessorPluginExtension
import dev.reformator.bytecodeprocessor.plugins.*
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URLClassLoader
import kotlin.jvm.java

plugins {
    kotlin("jvm")
    alias(libs.plugins.bytecode.processor)
    alias(libs.plugins.force.variant.java.version)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.bytecode.processor.intrinsics)
    compileOnly(project(":stacktrace-decoroutinator-common"))

    runtimeOnly(libs.ktor.io.jvm) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
    }

    implementation(libs.jupiter.api)
    implementation(libs.junit4)
    implementation(libs.coroutines.core.build)
    implementation(project(":tests:duplicate-entity-jar", "duplicateEntityJar"))
}

bytecodeProcessor {
    processors = listOf(
        GetCurrentFileNameProcessor,
        GetOwnerClassProcessor,
        LoadConstantProcessor
    )
}

// Resolved here, at project (script) scope, rather than inside the task registration lambda below -
// `the<T>()` there would resolve against the Task's own (empty) extension container, not the
// project's, since `this` inside tasks.register's lambda is the Task.
val bytecodeProcessorExecutionState = the<BytecodeProcessorPluginExtension>().executionState

val fillConstantProcessorTask: TaskProvider<*> = tasks.register("fillConstantProcessor") {
    val customLoaderJarTask = project(":tests:custom-loader").tasks.named<ShadowJar>("shadowJar")
    dependsOn(customLoaderJarTask)
    val customLoaderJarFile = customLoaderJarTask.flatMap { it.archiveFile }

    val bytecodeProcessorJarTask = project(":tests:bytecode-processor").tasks.named<Jar>("jar")
    dependsOn(bytecodeProcessorJarTask)
    val bytecodeProcessorJarFile = bytecodeProcessorJarTask.flatMap { it.archiveFile }

    val executionState = bytecodeProcessorExecutionState

    doLast {
        val customLoaderJarUri = customLoaderJarFile.get().asFile.toURI().toString()

        val addOpcodeTraceProcessor = URLClassLoader(
            arrayOf(bytecodeProcessorJarFile.get().asFile.toURI().toURL()),
            Processor::class.java.classLoader
        ).loadClass("dev.reformator.stacktracedecoroutinator.tests.bytecodeprocessor.AddOpcodeTraceProcessor")
            .getConstructor()
            .newInstance() as Processor

        executionState.initContext {
            LoadConstantProcessor.addValues(
                context = this,
                valuesByKeys = mapOf("customLoaderJarUri" to customLoaderJarUri)
            )
        }
        executionState.processors += addOpcodeTraceProcessor
    }
}

bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:-module"))
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

sourceSets {
    main {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}
