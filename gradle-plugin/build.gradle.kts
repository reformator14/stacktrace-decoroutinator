import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Base64

plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
    alias(libs.plugins.gradle.plugin.publish)
    id("dev.reformator.bytecodeprocessor")
    groovy
}

repositories {
    mavenCentral()
}

tasks.named<GroovyCompile>("compileGroovy") {
    val kotlinCompile = tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(kotlinCompile)
    doFirst {
        classpath = files(classpath + kotlinCompile.get().destinationDirectory.get().asFile)
    }
}

gradlePlugin {
    website = "https://github.com/reformator14/stacktrace-decoroutinator"
    vcsUrl = "https://github.com/reformator14/stacktrace-decoroutinator.git"
    plugins {
        create("decoroutinatorPlugin") {
            id = "dev.reformator.stacktracedecoroutinator"
            implementationClass = "dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPlugin"
            displayName = "Stacktrace Decoroutinator Gradle Plugin"
            description = "Gradle plugin for recovering stack trace in exceptions thrown in Kotlin coroutines"
            tags = listOf("kotlin", "coroutines", "debug", "kotlin-coroutines")
        }
        create("decoroutinatorAttributePlugin") {
            id = "dev.reformator.stacktracedecoroutinator.attribute"
            implementationClass = "dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorAttributePlugin"
            displayName = "Attribute helper for Decoroutinator Gradle Plugin"
            description = "Gradle plugin for setting attribute for artifacts that are transformed by Stacktrace Decoroutinator"
            tags = listOf("kotlin", "coroutines", "debug", "kotlin-coroutines")
        }
    }
}

afterEvaluate {
    tasks.named<Jar>("javadocJar") {
        from(tasks.named("dokkaJavadoc"))
    }
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    compileOnly(project(":intrinsics"))

    implementation(project(":stacktrace-decoroutinator-class-transformer"))
    implementation(project(":stacktrace-decoroutinator-runtime-settings"))
    implementation(project(":stacktrace-decoroutinator-provider"))
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.kotlin.gradle.plugin.api)
    implementation(libs.asm.utils)

    testImplementation(kotlin("test"))
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val embeddedDebugProbesStdlibJarTask =
        project(":gradle-plugin:embedded-debug-probes-stdlib").tasks.named<Jar>("jar")
    val embeddedDebugProbesXcoroutinesJarTask =
        project(":gradle-plugin:embedded-debug-probes-xcoroutines").tasks.named<Jar>("jar")
    val baseContinuationAccessorJarTask =
        project(":gradle-plugin:base-continuation-accessor").tasks.named<Jar>("jar")
    dependsOn(
        embeddedDebugProbesStdlibJarTask,
        embeddedDebugProbesXcoroutinesJarTask,
        baseContinuationAccessorJarTask
    )
    doLast {
        val embeddedDebugProbesStdlibJarBody =
            embeddedDebugProbesStdlibJarTask.get().archiveFile.get().asFile.readBytes()
        val embeddedDebugProbesXcoroutinesJarBody =
            embeddedDebugProbesXcoroutinesJarTask.get().archiveFile.get().asFile.readBytes()
        val baseContinuationAccessorJarBody = baseContinuationAccessorJarTask.get().archiveFile.get().asFile.readBytes()
        bytecodeProcessor {
            initContext {
                val base64Encoder = Base64.getEncoder()
                LoadConstantProcessor.addValues(this, mapOf(
                    "embeddedDebugProbesStdlibJarBase64"
                            to base64Encoder.encodeToString(embeddedDebugProbesStdlibJarBody),
                    "embeddedDebugProbesXcoroutinesJarBase64"
                            to base64Encoder.encodeToString(embeddedDebugProbesXcoroutinesJarBody),
                    "baseContinuationAccessorJarBase64"
                            to base64Encoder.encodeToString(baseContinuationAccessorJarBody)
                ))
            }
        }
    }
}

bytecodeProcessor {
    dependentProjects = listOf(
        project(":stacktrace-decoroutinator-generator-jvm"),
        project(":gradle-plugin:base-continuation-accessor")
    )
    processors = listOf(LoadConstantProcessor)
    initContext {
        LoadConstantProcessor.addValues(this, mapOf("version" to project.version.toString()))
    }
}

bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)

kotlin {
    jvmToolchain(8)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(project(":gradle-plugin:tests-gp").tasks.test)
    dependsOn(project(":gradle-plugin:jdk8-tests-gp").tasks.test)
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name.set("Stacktrace-decoroutinator Gradle plugin.")
                description.set("Library for recovering stack trace in exceptions thrown in Kotlin coroutines.")
                url.set("https://github.com/reformator14/stacktrace-decoroutinator")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("Denis Berestinskii")
                        email.set("berestinsky@gmail.com")
                        url.set("https://github.com/Anamorphosee")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/reformator14/stacktrace-decoroutinator.git")
                    developerConnection.set("scm:git:ssh://github.com:reformator14/stacktrace-decoroutinator.git")
                    url.set("http://github.com/reformator14/stacktrace-decoroutinator/tree/master")
                }
            }
        }
    }
}

signing {
    useGpgCmd()
}
