import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
    alias(libs.plugins.shadow)
    `maven-publish`
    signing
    alias(libs.plugins.delete.signature.checksums)
    alias(libs.plugins.force.variant.java.version)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(kotlin("stdlib"))

    implementation(project(":stacktrace-decoroutinator-jvm-agent-common")) {
        exclude(group = "org.jetbrains.kotlin")
    }
}

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
    exclude("META-INF/*.kotlin_module")
}

tasks.test {
    dependsOn(
        project(":stacktrace-decoroutinator-jvm-agent:jvm-agent-tests").tasks.test,
        project(":stacktrace-decoroutinator-jvm-agent:jvm-agent-jdk8-tests").tasks.test
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
