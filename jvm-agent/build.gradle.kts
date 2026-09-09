import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContextImpl
import dev.reformator.bytecodeprocessor.api.applyBytecodeProcessors
import dev.reformator.bytecodeprocessor.plugins.ChangeClassNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor
import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import dev.reformator.bytecodeprocessor.plugins.MakeStaticProcessor
import org.gradle.kotlin.dsl.named
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
    alias(libs.plugins.shadow)
    alias(libs.plugins.gr8)
    `maven-publish`
    signing
    alias(libs.plugins.delete.signature.checksums)
    alias(libs.plugins.bytecode.processor)
    alias(libs.plugins.force.variant.java.version)
}

repositories {
    mavenCentral()
    google() // com.android.tools:r8, resolved by the gr8 plugin below
}

dependencies {
    compileOnly(libs.bytecode.processor.intrinsics)
    compileOnly(project(":intrinsics"))

    implementation(project(":stacktrace-decoroutinator-provider"))
    implementation(project(":stacktrace-decoroutinator-runtime-settings"))
    implementation(project(":stacktrace-decoroutinator-jvm-agent-common"))
}

bytecodeProcessor {
    dependentProjects = listOf(project(":gradle-plugin:base-continuation-accessor"))
    processors = listOf(
        LoadConstantProcessor,
        MakeStaticProcessor,
        GetOwnerClassProcessor,
        ChangeClassNameProcessor
    )
}

// A single string constant is limited to 65535 UTF-8 bytes (JVM CONSTANT_Utf8_info) - common's
// base64-encoded jar already exceeds that, so it's split across a fixed number of chunk constants
// (see commonResidualJarBase64ChunkCount in dispatching-provider.kt) and concatenated at runtime.
val commonResidualJarBase64ChunkSize = 60000
val commonResidualJarBase64ChunkCount = 8

val buildRenamedCommonJar = tasks.register<ShadowJar>("buildRenamedCommonJar") {
    val commonProject = project(":stacktrace-decoroutinator-common")
    val commonCompileKotlinTask = commonProject.tasks.named<KotlinCompile>("compileKotlin")
    dependsOn(commonCompileKotlinTask, commonProject.tasks.named("compileJava"))
    from(commonCompileKotlinTask.map { it.destinationDirectory })

    failOnDuplicateEntries = true
    mergeServiceFiles()
    relocate("dev.reformator.stacktracedecoroutinator", "dev.reformator.stacktracedecoroutinator.jvmagentjar")
    exclude("META-INF/*.kotlin_module")
    archiveClassifier.set("renamed-common")
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val baseContinuationAccessorJarTask =
        project(":gradle-plugin:base-continuation-accessor").tasks.named<Jar>("jar")
    dependsOn(buildRenamedCommonJar, baseContinuationAccessorJarTask)
    doLast {
        val base64 = Base64.getEncoder().encodeToString(buildRenamedCommonJar.get().archiveFile.get().asFile.readBytes())
        val chunks = base64.chunked(commonResidualJarBase64ChunkSize)
        check(chunks.size <= commonResidualJarBase64ChunkCount) {
            "common's jar (base64: ${base64.length} chars) needs ${chunks.size} chunks of " +
                "$commonResidualJarBase64ChunkSize chars, but only $commonResidualJarBase64ChunkCount chunk " +
                "constants are declared in dispatching-provider.kt - add more."
        }
        // The "regular" base-continuation-accessor class is compiled by the wholly separate
        // gradle-plugin:base-continuation-accessor module, whose jar predates shadowJar's own
        // relocate("dev.reformator.stacktracedecoroutinator", ...) below - it still references
        // provider's *unrelocated* BaseContinuationAccessor/BaseContinuationAccessorProvider
        // interface names, which no longer exist under those names once this jar is shaded. Extract
        // it, rewrite just those two references onto this jar's own jvmagentjar namespace via
        // ChangeClassNameProcessor (the same processor Shadow's relocate would have applied if this
        // class were compiled as part of this module instead of embedded as a base64 blob), and
        // re-zip before encoding - the class's own name (kotlin.coroutines.jvm.internal.*) is left
        // untouched, matching regularAccessorClassName/loadRegularAccessor's exact-name lookup.
        val baseContinuationAccessorExtractedDir =
            layout.buildDirectory.dir("baseContinuationAccessorRenamed").get().asFile
        baseContinuationAccessorExtractedDir.deleteRecursively()
        baseContinuationAccessorExtractedDir.mkdirs()
        ZipInputStream(baseContinuationAccessorJarTask.get().archiveFile.get().asFile.inputStream()).use { zipIn ->
            while (true) {
                val entry = zipIn.nextEntry ?: break
                if (!entry.isDirectory) {
                    val outFile = baseContinuationAccessorExtractedDir.resolve(entry.name)
                    outFile.parentFile.mkdirs()
                    outFile.outputStream().use { zipIn.copyTo(it) }
                }
            }
        }
        val renameContext = BytecodeProcessorContextImpl()
        ChangeClassNameProcessor.add(renameContext, mapOf(
            "dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor" to
                    "dev.reformator.stacktracedecoroutinator.jvmagentjar.provider.internal.BaseContinuationAccessor",
            "dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider" to
                    "dev.reformator.stacktracedecoroutinator.jvmagentjar.provider.internal.BaseContinuationAccessorProvider"
        ))
        baseContinuationAccessorExtractedDir.applyBytecodeProcessors(listOf(ChangeClassNameProcessor), renameContext)
        val baseContinuationAccessorJarBody = ByteArrayOutputStream().also { byteStream ->
            ZipOutputStream(byteStream).use { zipOut ->
                baseContinuationAccessorExtractedDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    zipOut.putNextEntry(ZipEntry(file.relativeTo(baseContinuationAccessorExtractedDir).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }.toByteArray()
        bytecodeProcessor {
            initContext {
                LoadConstantProcessor.addValues(this, buildMap {
                    repeat(commonResidualJarBase64ChunkCount) { index ->
                        put("commonResidualJarBase64Chunk$index", chunks.getOrElse(index) { "" })
                    }
                    put(
                        "baseContinuationAccessorJarBase64",
                        Base64.getEncoder().encodeToString(baseContinuationAccessorJarBody)
                    )
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
            "Premain-Class" to "dev.reformator.stacktracedecoroutinator.jvmagentjar.jvmagent.DecoroutinatorAgentKt"
        ))
    }
    relocate("dev.reformator.stacktracedecoroutinator", "dev.reformator.stacktracedecoroutinator.jvmagentjar")
    relocate("org.objectweb.asm", "dev.reformator.stacktracedecoroutinator.jvmagentjar.asm")
    relocate("dev.reformator.kmetarepack", "dev.reformator.stacktracedecoroutinator.jvmagentjar.kmeta")
    relocate("kotlin", "dev.reformator.stacktracedecoroutinator.jvmagentjar.kotlin") {
        // class-transformer/spec-method-builder use real, unrelocated kotlin.* class names as DATA
        // (matched against un-relocated target application bytecode) - string constants must not
        // be rewritten, only actual structural type references (checkcast/instanceof/descriptors).
        skipStringConstants = true
    }
    exclude("META-INF/*.kotlin_module")
    archiveClassifier.set("shadow")
}

gr8 {
    create("minimized") {
        addProgramJarsFrom(tasks.shadowJar.flatMap { it.archiveFile })
        proguardFile("jvm-agent-r8-rules.pro")
    }
}

val gr8MinimizedJarTask = tasks.named("gr8MinimizedShadowedJar")

// Gr8Task has two @OutputFiles (the shrunk jar and R8's mapping file) - single out the jar.
fun gr8MinimizedJarFile() = gr8MinimizedJarTask.get().outputs.files.single { it.extension == "jar" }

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
            // The gr8-minimized jar (shadowJar's output, R8-shrunk - see gr8 { } above) is the
            // published artifact, not shadowJar's own output directly. No from(components["shadow"])
            // (or any other component): the jar is fully self-contained (everything shaded in), so an
            // empty POM <dependencies> section - what an explicit artifact()-only publication produces
            // - is exactly right, same as what Shadow's component would have produced anyway.
            artifact(gr8MinimizedJarFile()) {
                classifier = ""
                builtBy(gr8MinimizedJarTask)
            }
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
