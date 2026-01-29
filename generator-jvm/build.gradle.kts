import dev.reformator.bytecodeprocessor.plugins.*
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
    id("dev.reformator.bytecodeprocessor")
    id("dev.reformator.forcevariantjavaversion")
    id("decoroutinatorTransformBaseContinuation")
}

repositories {
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    compileOnly(project(":intrinsics"))

    api(project(":stacktrace-decoroutinator-common"))

    implementation(libs.asm.utils)
    implementation(project(":stacktrace-decoroutinator-provider"))
    implementation(project(":stacktrace-decoroutinator-spec-method-builder"))

    testImplementation(kotlin("test"))
    testImplementation(project(":test-utils"))
    testImplementation(project(":test-utils-jvm"))

    testRuntimeOnly(project(":stacktrace-decoroutinator-mh-invoker"))
    testRuntimeOnly(project(":test-utils:base-continuation-accessor-stub"))
}

bytecodeProcessor {
    dependentProjects = listOf(project(":stacktrace-decoroutinator-common"))
    processors = listOf(
        ChangeClassNameProcessor,
        MakeStaticProcessor,
        GetOwnerClassProcessor,
        LoadConstantProcessor
    )
}

afterEvaluate {
    configurations.testRuntimeClasspath.get().attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(project(":generator-jvm:jdk8-tests-g").tasks.test)
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
                name.set("Stacktrace-decoroutinator JVM common lib.")
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
