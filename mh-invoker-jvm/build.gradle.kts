import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContextImpl
import dev.reformator.bytecodeprocessor.api.applyBytecodeProcessors
import dev.reformator.bytecodeprocessor.plugins.*
import org.apache.commons.io.output.ByteArrayOutputStream
import org.gradle.kotlin.dsl.named
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.apply
import kotlin.sequences.forEach

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

    implementation(project(":stacktrace-decoroutinator-provider"))
    implementation(project(":stacktrace-decoroutinator-common"))

    testImplementation(kotlin("test"))
    testImplementation(project(":stacktrace-decoroutinator-provider"))
    testImplementation(project(":test-utils"))
    testImplementation(project(":test-utils-jvm"))
    testRuntimeOnly(project(":stacktrace-decoroutinator-generator-jvm"))
    testRuntimeOnly(project(":test-utils:base-continuation-accessor-stub"))
}

afterEvaluate {
    configurations.testRuntimeClasspath.get().attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
}

bytecodeProcessor {
    dependentProjects = listOf(project(":stacktrace-decoroutinator-common"))
    processors = setOf(
        ChangeClassNameProcessor,
        ChangeInvocationsOwnerProcessor,
        GetOwnerClassProcessor,
        LoadConstantProcessor
    )
}

private fun File.clearDir() {
    listFiles()!!.forEach {
        if (it.isDirectory) {
            it.deleteRecursively()
        } else {
            it.delete()
        }
    }
}

private val File.isClass: Boolean
    get() = isFile && name.endsWith(".class") && name != "module-info.class"

private fun File.copyClassesTo(output: File) {
    walk().filter { it.isClass }.forEach { file ->
        val outputFile = output.resolve(file.relativeTo(this))
        outputFile.parentFile.mkdirs()
        file.copyTo(outputFile)
    }
}

private val File.classNameSequence: Sequence<String>
    get() = walk().filter { it.isClass }.map {
        it.relativeTo(this).path.removeSuffix(".class").replace(File.separator, ".")
    }

private fun File.zipDirectoryToArray(): ByteArray {
    val bufferOutput = ByteArrayOutputStream()
    ZipOutputStream(bufferOutput).use { output ->
        walk().forEach { file ->
            val entryName = file.toRelativeString(this).replace(File.separator, "/")
            if (file.isDirectory) {
                output.putNextEntry(ZipEntry("$entryName/").also {
                    it.method = ZipEntry.DEFLATED
                })
            } else {
                val buffer = file.readBytes()
                output.putNextEntry(ZipEntry(entryName).also {
                    it.method = ZipEntry.DEFLATED
                    it.size = buffer.size.toLong()
                })
                output.write(buffer)
            }
        }
    }
    return bufferOutput.toByteArray()
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val mhInvokerProject = project(":stacktrace-decoroutinator-mh-invoker")
    val mhInvokerCompileKotlinTask = mhInvokerProject.tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(mhInvokerCompileKotlinTask)
    doLast {
        val tempDir = temporaryDir
        tempDir.clearDir()
        mhInvokerCompileKotlinTask.get().destinationDirectory.get().asFile.copyClassesTo(tempDir)
        val packageNames = listOf("dev.reformator.stacktracedecoroutinator.mhinvoker", "dcunknown")
        val changeClassNameParameters = tempDir.classNameSequence.map { className ->
            val packageName = packageNames.first { className.startsWith(it) }
            val newClassName = "${packageName}jvm${className.removePrefix(packageName)}"
            className to newClassName
        }.toMap()
        tempDir.applyBytecodeProcessors(
            processors = listOf(ChangeClassNameProcessor),
            context = BytecodeProcessorContextImpl().apply {
                ChangeClassNameProcessor.add(this, changeClassNameParameters)
            }
        )
        bytecodeProcessor {
            initContext {
                LoadConstantProcessor.addValues(this, mapOf(
                    "regularMethodHandleJarBase64" to Base64.getEncoder().encodeToString(tempDir.zipDirectoryToArray())
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
                name.set("Stack Trace Decoroutinator MethodHandle JVM invoker.")
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
