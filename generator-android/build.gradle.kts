import dev.reformator.bytecodeprocessor.plugins.*
import org.jetbrains.dokka.gradle.AbstractDokkaTask

plugins {
    alias(libs.plugins.android.library)
    kotlin("android")
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
    id("dev.reformator.bytecodeprocessor")
    id("decoroutinatorTransformBaseContinuation")
}

repositories {
    mavenCentral()
    google()
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.generatorandroid"
    compileSdk = 36
    defaultConfig {
        minSdk = 14
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources.pickFirsts.add("META-INF/*")
    }
    kotlin {
        jvmToolchain(8)
    }
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    compileOnly(project(":intrinsics"))

    api(project(":stacktrace-decoroutinator-common"))

    implementation(project(":stacktrace-decoroutinator-provider"))
    implementation(libs.dalvik.dx)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.jdk8.build)

    testRuntimeOnly(project(":stacktrace-decoroutinator-mh-invoker"))
    testRuntimeOnly(project(":test-utils:base-continuation-accessor-stub"))
}

bytecodeProcessor {
    dependentProjects = listOf(project(":stacktrace-decoroutinator-common"))
    processors = listOf(
        ChangeClassNameProcessor
    )
}

afterEvaluate {
    configurations["debugUnitTestRuntimeClasspath"].attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
    configurations["releaseUnitTestRuntimeClasspath"].attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
}

val dokkaJavadocsJar = tasks.register<Jar>("dokkaJavadocsJar") {
    val dokkaJavadocTask = tasks.named<AbstractDokkaTask>("dokkaJavadoc").get()
    dependsOn(dokkaJavadocTask)
    archiveClassifier.set("javadoc")
    from(dokkaJavadocTask.outputDirectory)
}

val mavenPublicationName = "maven"

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>(mavenPublicationName) {
                from(components["release"])
                artifact(dokkaJavadocsJar)
                pom {
                    name.set("Stacktrace-decoroutinator Android runtime class generator.")
                    description.set("Android library for recovering stack trace in exceptions thrown in Kotlin coroutines.")
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
}
