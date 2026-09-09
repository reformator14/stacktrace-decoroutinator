import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
    alias(libs.plugins.delete.signature.checksums)
    alias(libs.plugins.bytecode.processor)
    alias(libs.plugins.force.variant.java.version)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.bytecode.processor.intrinsics)
    compileOnly(project(":intrinsics"))

    implementation(project(":stacktrace-decoroutinator-jvm-agent-common"))
    implementation(project(":stacktrace-decoroutinator-provider"))
    implementation(libs.byte.buddy.agent)

    api(project(":stacktrace-decoroutinator-common"))

    testCompileOnly(project(":intrinsics"))

    testImplementation(kotlin("test"))
    testImplementation(project(":tests:methods-with-spaces-tests"))
    testImplementation(libs.junit5.platform.launcher)
}

bytecodeProcessor {
    dependentProjects = listOf(project(":gradle-plugin:base-continuation-accessor"))
    processors = listOf(LoadConstantProcessor)
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val baseContinuationAccessorJarTask =
        project(":gradle-plugin:base-continuation-accessor").tasks.named<Jar>("jar")
    dependsOn(baseContinuationAccessorJarTask)
    doLast {
        val baseContinuationAccessorJarBody =
            baseContinuationAccessorJarTask.get().archiveFile.get().asFile.readBytes()
        bytecodeProcessor {
            initContext {
                LoadConstantProcessor.addValues(this, mapOf(
                    "baseContinuationAccessorJarBase64"
                            to Base64.getEncoder().encodeToString(baseContinuationAccessorJarBody)
                ))
            }
        }
    }
}

bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)

val testReloadBaseConfigurationTask = tasks.register<Test>("testReloadBaseConfiguration") {
    useJUnitPlatform()
    classpath = tasks.test.get().classpath
}
tasks.test {
    useJUnitPlatform()
    systemProperty("installDecoroutinator", true)
    dependsOn(
        testReloadBaseConfigurationTask,
        project(":stacktrace-decoroutinator-jvm:jvm-jdk8-tests").tasks.test
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:-module")
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
    test {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}

val dokkaJavadocsJar = tasks.register<Jar>("dokkaJavadocsJar") {
    val dokkaJavadocTask = tasks.named<AbstractDokkaTask>("dokkaJavadoc").get()
    dependsOn(dokkaJavadocTask)
    archiveClassifier.set("javadoc")
    from(dokkaJavadocTask.outputDirectory)
}

val mavenPublicationName = "maven"

publishing {
    publications {
        create<MavenPublication>(mavenPublicationName) {
            from(components["java"])
            artifact(dokkaJavadocsJar)
            artifact(tasks.named("kotlinSourcesJar"))
            pom {
                name.set("Stacktrace-decoroutinator JVM.")
                description.set("JVM library for recovering stack trace in exceptions thrown in Kotlin coroutines.")
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
    sign(publishing.publications[mavenPublicationName])
}
