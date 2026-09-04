import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.plugins.*
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

    implementation(libs.ktor.utils)
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

val fillConstantProcessorTask: TaskProvider<*> = tasks.register("fillConstantProcessor") {
    val customLoaderJarTask = project(":tests:custom-loader").tasks.named<ShadowJar>("shadowJar")
    dependsOn(customLoaderJarTask)

    val bytecodeProcessorJarTask = project(":tests:bytecode-processor").tasks.named<Jar>("jar")
    dependsOn(bytecodeProcessorJarTask)

    doLast {
        val customLoaderJarUri = customLoaderJarTask.get().archiveFile.get().asFile.toURI().toString()

        val addOpcodeTraceProcessor = URLClassLoader(
            arrayOf(bytecodeProcessorJarTask.get().archiveFile.get().asFile.toURI().toURL()),
            Processor::class.java.classLoader
        ).loadClass("dev.reformator.stacktracedecoroutinator.tests.bytecodeprocessor.AddOpcodeTraceProcessor")
            .getConstructor()
            .newInstance() as Processor

        bytecodeProcessor {
            initContext {
                LoadConstantProcessor.addValues(
                    context = this,
                    valuesByKeys = mapOf("customLoaderJarUri" to customLoaderJarUri)
                )
            }
            processors += addOpcodeTraceProcessor
        }
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
