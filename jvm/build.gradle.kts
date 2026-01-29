import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
    jacoco
    id("dev.reformator.forcevariantjavaversion")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":stacktrace-decoroutinator-common"))

    implementation(project(":stacktrace-decoroutinator-jvm-agent-common"))
    implementation(project(":stacktrace-decoroutinator-provider"))
    implementation(libs.byte.buddy.agent)

    compileOnly(project(":intrinsics"))

    testImplementation(kotlin("test"))
    testImplementation(project(":test-utils"))
    testImplementation(project(":test-utils-jvm"))
    testImplementation(libs.kotlinx.coroutines.jdk8.build)

    testCompileOnly(project(":intrinsics"))
}

tasks.test {
    useJUnitPlatform()
    extensions.configure(JacocoTaskExtension::class) {
        includes = listOf("JacocoInstrumentedMethodTest*")
    }
    systemProperty("testReloadBaseConfiguration", false)
}
run {
    val jdk8TestsProject = project(":jvm:jdk8-tests-j")
    val currentProject = project
    jdk8TestsProject.afterEvaluate {
        currentProject.tasks.test.dependsOn(jdk8TestsProject.tasks.test)
    }
}


val testReloadBaseConfigurationTask = tasks.register<Test>("testReloadBaseConfiguration") {
    useJUnitPlatform()
    classpath = tasks.test.get().classpath
    extensions.configure(JacocoTaskExtension::class) {
        excludes = listOf("*")
    }
    systemProperty("testReloadBaseConfiguration", true)
}
tasks.test.get().dependsOn(testReloadBaseConfigurationTask)

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
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
