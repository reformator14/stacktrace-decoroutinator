import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContextImpl
import dev.reformator.bytecodeprocessor.api.applyBytecodeProcessors
import dev.reformator.bytecodeprocessor.gradleplugin.BytecodeProcessorPluginExtension
import dev.reformator.bytecodeprocessor.plugins.*
import org.apache.commons.io.output.ByteArrayOutputStream
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
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
    implementation(project(":stacktrace-decoroutinator-common"))
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

// All helpers below are local to this task registration (not top-level in the script) so that
// fillConstantProcessorTask's doLast - an execution-time closure the configuration cache must be able
// to serialize - doesn't implicitly capture the build script object. A top-level fun/val in a
// .gradle.kts file is compiled as a member of the synthetic script class, so calling it from doLast
// would otherwise require serializing that script instance, which the configuration cache rejects.
// executionState is resolved here, at project (script) scope, rather than inside the task registration
// lambda below - `the<T>()` there would resolve against the Task's own (empty) extension container,
// not the project's, since `this` inside tasks.register's lambda is the Task.
val bytecodeProcessorExecutionState = the<BytecodeProcessorPluginExtension>().executionState

val fillConstantProcessorTask: TaskProvider<*> = tasks.register("fillConstantProcessor") {
    val mhInvokerProject = project(":stacktrace-decoroutinator-mh-invoker")
    val mhInvokerCompileKotlinTask = mhInvokerProject.tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(mhInvokerCompileKotlinTask)
    val mhInvokerDestinationDirectory = mhInvokerCompileKotlinTask.flatMap { it.destinationDirectory }
    val executionState = bytecodeProcessorExecutionState

    fun File.clearDir() {
        listFiles()!!.forEach {
            if (it.isDirectory) {
                it.deleteRecursively()
            } else {
                it.delete()
            }
        }
    }

    // Kotlin doesn't allow local *extension properties* (only local extension functions), so these
    // are functions (isClass(), classNameSequence()) rather than the more idiomatic `val X.y` form.
    fun File.isClass(): Boolean =
        isFile && name.endsWith(".class") && name != "module-info.class"

    fun File.copyClassesTo(output: File) {
        walk().filter { it.isClass() }.forEach { file ->
            val outputFile = output.resolve(file.relativeTo(this))
            outputFile.parentFile.mkdirs()
            file.copyTo(outputFile)
        }
    }

    fun File.classNameSequence(): Sequence<String> =
        walk().filter { it.isClass() }.map {
            it.relativeTo(this).path.removeSuffix(".class").replace(File.separator, ".")
        }

    fun File.zipDirectoryToArray(): ByteArray {
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

    fun File.renameClasses(namePrefixes: List<String>, prefixAppend: String) {
        val changeClassNameParameters = classNameSequence().associateWith { className ->
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

    doLast {
        val tempDir = temporaryDir
        tempDir.clearDir()
        mhInvokerDestinationDirectory.get().asFile.copyClassesTo(tempDir)
        tempDir.renameClasses(
            namePrefixes = listOf("dev.reformator.stacktracedecoroutinator.mhinvoker", "dcunknown"),
            prefixAppend = "jvm"
        )
        executionState.initContext {
            LoadConstantProcessor.addValues(this, mapOf(
                "regularMethodHandleJarBase64" to Base64.getEncoder().encodeToString(tempDir.zipDirectoryToArray())
            ))
        }
    }
}

bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)

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
