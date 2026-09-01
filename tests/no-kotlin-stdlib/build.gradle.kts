import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import dev.reformator.bytecodeprocessor.plugins.RemoveKotlinStdlibProcessor
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.bytecode.processor)
    // Without this, Gradle's dependency resolution reports this module's TargetJvmVersion as 9
    // (from java.sourceCompatibility below, needed only for module-info.java) even though the
    // actual Kotlin bytecode targets 8 (kotlin.compilerOptions.jvmTarget below) - same reason
    // provider/runtime-settings apply it, and jvm-agent:jdk8-tests-no-kotlin-stdlib (JDK 8
    // toolchain) needs it to be able to depend on this module at all.
    alias(libs.plugins.force.variant.java.version)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.bytecode.processor.intrinsics)
    // kotlinc needs kotlin-stdlib resolvable to compile this module's module-info.java (every
    // Kotlin class carries a structural @kotlin.Metadata annotation reference) - compileOnly (not
    // implementation, which the disabled kotlin.stdlib.default.dependency would otherwise have
    // added) keeps it off this module's actual runtime classpath, matching provider/runtime-settings.
    compileOnly(dependencies.kotlin("stdlib", libs.versions.kotlin.build.get()))

    implementation(libs.jupiter.api)
}

bytecodeProcessor {
    processors = listOf(
        RemoveKotlinStdlibProcessor,
        LoadConstantProcessor
    )
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val customLoaderJarTask = project(":tests:custom-loader").tasks.named<ShadowJar>("shadowJar")
    dependsOn(customLoaderJarTask)

    // Resolved via a detached configuration, never a real dependency edge of this module - this
    // module must stay free of any real kotlin-stdlib dependency (see NoKotlinStdlibTest's class
    // doc for why).
    val kotlinStdlibConfig = configurations.detachedConfiguration(
        dependencies.create(dependencies.kotlin("stdlib", libs.versions.kotlin.build.get()))
    )

    doLast {
        bytecodeProcessor {
            initContext {
                LoadConstantProcessor.addValues(
                    context = this,
                    valuesByKeys = mapOf(
                        "customLoaderJarPath" to customLoaderJarTask.get().archiveFile.get().asFile.absolutePath,
                        "kotlinStdlibJarPath" to kotlinStdlibConfig.files
                            .single { it.name == "kotlin-stdlib-${libs.versions.kotlin.build.get()}.jar" }
                            .absolutePath
                    )
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
    }
}

sourceSets {
    main {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}
