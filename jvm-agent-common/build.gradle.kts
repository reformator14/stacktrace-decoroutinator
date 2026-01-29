import dev.reformator.bytecodeprocessor.plugins.ChangeClassNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor
import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import org.gradle.kotlin.dsl.named
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Base64

plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
    id("dev.reformator.bytecodeprocessor")
    id("dev.reformator.forcevariantjavaversion")
}

repositories {
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    compileOnly(project(":intrinsics"))

    implementation(project(":stacktrace-decoroutinator-provider"))
    implementation(project(":stacktrace-decoroutinator-runtime-settings"))
    implementation(project(":stacktrace-decoroutinator-class-transformer"))
    implementation(libs.asm.utils)

    runtimeOnly(project(":stacktrace-decoroutinator-mh-invoker"))
    runtimeOnly(project(":stacktrace-decoroutinator-generator-jvm"))

    testCompileOnly(project(":intrinsics"))

    testImplementation(kotlin("test"))
}

bytecodeProcessor {
    dependentProjects = listOf(
        project(":stacktrace-decoroutinator-provider"),
        project(":gradle-plugin:base-continuation-accessor"),
        project(":jvm-agent-common:suspend-class-stub")
    )
    processors = listOf(
        GetOwnerClassProcessor,
        ChangeClassNameProcessor,
        LoadConstantProcessor
    )
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val baseContinuationAccessorJarTask =
        project(":gradle-plugin:base-continuation-accessor").tasks.named<Jar>("jar")
    val suspendClassStubCompileTask =
        project(":jvm-agent-common:suspend-class-stub").tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(baseContinuationAccessorJarTask, suspendClassStubCompileTask)
    doLast {
        val baseContinuationAccessorJarBody = baseContinuationAccessorJarTask.get().archiveFile.get().asFile.readBytes()
        bytecodeProcessor {
            initContext {
                val base64Encoder = Base64.getEncoder()
                val jvmAgentCommonSuspendClassBody = run {
                    val className =
                        LoadConstantProcessor.getValue(this, "jvmAgentCommonSuspendClassName")!!
                    val classNameComponents = className.split('.')
                    var dir = suspendClassStubCompileTask.get().destinationDirectory.get()
                    for (i in 0 until classNameComponents.lastIndex) {
                        dir = dir.dir(classNameComponents[i])
                    }
                    dir.file("${classNameComponents.last()}.class").asFile.readBytes()
                }
                LoadConstantProcessor.addValues(this, mapOf(
                    "baseContinuationAccessorJarBase64"
                            to base64Encoder.encodeToString(baseContinuationAccessorJarBody),
                    "jvmAgentCommonSuspendClassBodyBase64"
                            to base64Encoder.encodeToString(jvmAgentCommonSuspendClassBody)
                ))
            }
        }
    }
}

bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)

tasks.test {
    useJUnitPlatform()
}

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
                name.set("Stacktrace-decoroutinator JVM agent common lib.")
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
    sign(publishing.publications[mavenPublicationName])
}
