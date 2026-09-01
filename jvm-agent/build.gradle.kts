import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import org.gradle.kotlin.dsl.named
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
    alias(libs.plugins.shadow)
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

    implementation(project(":stacktrace-decoroutinator-provider"))
    implementation(project(":stacktrace-decoroutinator-runtime-settings"))
    implementation(project(":stacktrace-decoroutinator-jvm-agent-common")) {
        // common is never bundled here - it's embedded (see fillConstantProcessorTask below) and
        // defined into isolated, per-target-classloader copies at runtime by DispatchingProvider,
        // so it never needs to interoperate with THIS module's own (relocated, see shadowJar below)
        // kotlin-stdlib copy.
        exclude(group = "dev.reformator.stacktracedecoroutinator", module = "stacktrace-decoroutinator-common")
    }
}

bytecodeProcessor {
    processors = listOf(LoadConstantProcessor)
}

// A single string constant is limited to 65535 UTF-8 bytes (JVM CONSTANT_Utf8_info) - common's
// base64-encoded jar already exceeds that, so it's split across a fixed number of chunk constants
// (see commonResidualJarBase64ChunkCount in dispatching-provider.kt) and concatenated at runtime.
val commonResidualJarBase64ChunkSize = 60000
val commonResidualJarBase64ChunkCount = 8

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val commonJarTask = project(":stacktrace-decoroutinator-common").tasks.named<Jar>("jar")
    dependsOn(commonJarTask)
    doLast {
        val base64 = Base64.getEncoder().encodeToString(commonJarTask.get().archiveFile.get().asFile.readBytes())
        val chunks = base64.chunked(commonResidualJarBase64ChunkSize)
        check(chunks.size <= commonResidualJarBase64ChunkCount) {
            "common's jar (base64: ${base64.length} chars) needs ${chunks.size} chunks of " +
                "$commonResidualJarBase64ChunkSize chars, but only $commonResidualJarBase64ChunkCount chunk " +
                "constants are declared in dispatching-provider.kt - add more."
        }
        bytecodeProcessor {
            initContext {
                LoadConstantProcessor.addValues(this, buildMap {
                    for (i in 0 until commonResidualJarBase64ChunkCount) {
                        put("commonResidualJarBase64Chunk$i", chunks.getOrElse(i) { "" })
                    }
                })
            }
        }
    }
}

bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)

tasks.shadowJar {
    failOnDuplicateEntries = true
    mergeServiceFiles()
    manifest {
        attributes(mapOf(
            "Premain-Class" to "dev.reformator.stacktracedecoroutinator.jvmagent.DecoroutinatorAgentKt"
        ))
    }
    archiveClassifier.set("")
    relocate("org.objectweb.asm", "dev.reformator.stacktracedecoroutinator.jvmagent.asmrepack")
    relocate("dev.reformator.kmetarepack", "dev.reformator.stacktracedecoroutinator.jvmagent.kmetarepack")
    relocate("kotlin", "dev.reformator.stacktracedecoroutinator.jvmagent.kotlinrepack") {
        // class-transformer/spec-method-builder use real, unrelocated kotlin.* class names as DATA
        // (matched against un-relocated target application bytecode) - string constants must not
        // be rewritten, only actual structural type references (checkcast/instanceof/descriptors).
        skipStringConstants = true
    }
    exclude("META-INF/*.kotlin_module")
}

tasks.test {
    dependsOn(
        project(":stacktrace-decoroutinator-jvm-agent:jvm-agent-tests").tasks.test,
        project(":stacktrace-decoroutinator-jvm-agent:jvm-agent-jdk8-tests").tasks.test,
        project(":stacktrace-decoroutinator-jvm-agent:jvm-agent-tests-no-kotlin-stdlib").tasks.test,
        project(":stacktrace-decoroutinator-jvm-agent:jvm-agent-jdk8-tests-no-kotlin-stdlib").tasks.test
    )
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
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
            from(components["shadow"])
            artifact(dokkaJavadocsJar)
            artifact(tasks.named("kotlinSourcesJar"))
            pom {
                name.set("Stacktrace-decoroutinator JVM agent.")
                description.set("JVM agent for recovering stack trace in exceptions thrown in Kotlin coroutines.")
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
