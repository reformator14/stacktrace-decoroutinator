import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.reformator.bytecodeprocessor.plugins.*
import org.gradle.kotlin.dsl.bytecodeProcessorFrom
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.bytecode.processor)
    alias(libs.plugins.force.variant.java.version)
}

repositories {
    mavenCentral()
}

val bytecodeProcessorConfig: Configuration = configurations.create("bytecodeProcessor") {
    isCanBeResolved = true
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

    add(bytecodeProcessorConfig.name, project(":tests:bytecode-processor"))
}

bytecodeProcessor {
    processors = listOf(
        GetCurrentFileNameProcessor,
        GetOwnerClassProcessor,
        LoadConstantProcessor,
        bytecodeProcessorFrom(
            configuration = bytecodeProcessorConfig,
            processorClassName = "dev.reformator.stacktracedecoroutinator.tests.bytecodeprocessor.AddOpcodeTraceProcessor"
        )
    )
}

val fillConstantProcessorTask: TaskProvider<*> = tasks.register("fillConstantProcessor") {
    val customLoaderProject = project(":tests:custom-loader")
    val customLoaderJarTask = customLoaderProject.tasks.named<ShadowJar>("shadowJar")
    dependsOn(customLoaderJarTask)
    doLast {
        val customLoaderJarUri = customLoaderJarTask.get().archiveFile.get().asFile.toURI().toString()
        bytecodeProcessor {
            initContext {
                LoadConstantProcessor.addValues(
                    context = this,
                    valuesByKeys = mapOf("customLoaderJarUri" to customLoaderJarUri)
                )
            }
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
