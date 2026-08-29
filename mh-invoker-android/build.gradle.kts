import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContextImpl
import dev.reformator.bytecodeprocessor.api.applyBytecodeProcessors
import dev.reformator.bytecodeprocessor.plugins.*
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Base64
import kotlin.apply

plugins {
    alias(libs.plugins.android.library)
    kotlin("android")
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
    alias(libs.plugins.delete.signature.checksums)
    alias(libs.plugins.bytecode.processor)
}

repositories {
    mavenCentral()
    google()
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.mhinvokerandroid"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    defaultConfig {
        minSdk = 14
    }
    packaging {
        resources.pickFirsts.add("META-INF/*")
    }
    kotlin {
        jvmToolchain(8)
    }
}

dependencies {
    compileOnly(libs.bytecode.processor.intrinsics)

    implementation(project(":stacktrace-decoroutinator-common"))
}

bytecodeProcessor {
    dependentProjects = listOf(project(":stacktrace-decoroutinator-common"))
    processors = listOf(
        ChangeClassNameProcessor,
        LoadConstantProcessor
    )
}

val fillConstantProcessorTask: TaskProvider<*> = tasks.register("fillConstantProcessor") {
    val mhInvokerProject = project(":stacktrace-decoroutinator-mh-invoker")
    val mhInvokerCompileKotlinTask = mhInvokerProject.tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(mhInvokerCompileKotlinTask)
    doLast {
        val tempDir = temporaryDir
        tempDir.clearDir()
        mhInvokerCompileKotlinTask.get().destinationDirectory.get().asFile.copyClassesTo(tempDir)
        tempDir.renameClasses(
            namePrefixes = listOf("dev.reformator.stacktracedecoroutinator.mhinvoker", "dcunknown"),
            prefixAppend = "android"
        )
        providers.exec {
            setCommandLine((
                sequenceOf(
                    "${android.sdkDirectory}/build-tools/${android.buildToolsVersion}/d8",
                    "--min-api", "26",
                    "--output", tempDir.absolutePath
                ) + tempDir.walk()
                    .filter { it.isFile && it.name.endsWith(".class") }
                    .map { it.absolutePath }
            ).asIterable())
        }.result.get().rethrowFailure()
        bytecodeProcessor {
            initContext {
                LoadConstantProcessor.addValues(this, mapOf(
                    "regularMethodHandleDexBase64" to
                            Base64.getEncoder().encodeToString(tempDir.resolve("classes.dex").readBytes())
                ))
            }
        }
    }
}

bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)

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
                    name.set("Stacktrace-decoroutinator Android runtime MethodHandle invoker.")
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

fun File.clearDir() {
    listFiles()!!.forEach {
        if (it.isDirectory) {
            it.deleteRecursively()
        } else {
            it.delete()
        }
    }
}

val File.isClass: Boolean
    get() = isFile && name.endsWith(".class") && name != "module-info.class"

fun File.copyClassesTo(output: File) {
    walk().filter { it.isClass }.forEach { file ->
        val outputFile = output.resolve(file.relativeTo(this))
        outputFile.parentFile.mkdirs()
        file.copyTo(outputFile)
    }
}

fun File.renameClasses(namePrefixes: List<String>, prefixAppend: String) {
    val changeClassNameParameters = classNameSequence.associateWith { className ->
        val prefix = namePrefixes.first { className.startsWith(it) }
        "${prefix}${prefixAppend}${className.removePrefix(prefix)}"
    }
    applyBytecodeProcessors(
        processors = listOf(ChangeClassNameProcessor),
        context = BytecodeProcessorContextImpl().apply {
            ChangeClassNameProcessor.add(this, changeClassNameParameters)
        }
    )
}

val File.classNameSequence: Sequence<String>
    get() = walk().filter { it.isClass }.map {
        it.relativeTo(this).path.removeSuffix(".class").replace(File.separator, ".")
    }
