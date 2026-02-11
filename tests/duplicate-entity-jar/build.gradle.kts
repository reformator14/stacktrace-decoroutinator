import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    dependencies {
        classpath(libs.ant)
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.bytecode.processor)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.bytecode.processor.intrinsics)
}

bytecodeProcessor {
    processors = setOf(
        GetCurrentFileNameProcessor,
        GetOwnerClassProcessor
    )
}

tasks.test {
    useJUnitPlatform()
}

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
}

val duplicateEntityJarConfig: Configuration = configurations.create("duplicateEntityJar")

val duplicateEntityJarTask: TaskProvider<*> = tasks.register("duplicateEntityJar") {
    val compileJavaTask = tasks.named<AbstractCompile>("compileJava")
    val compileKotlinTask = tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(compileJavaTask, compileKotlinTask)
    val outputFile = layout.buildDirectory.file("duplicate-entity.jar")
    inputs.dir(compileJavaTask.get().destinationDirectory)
    inputs.dir(compileKotlinTask.get().destinationDirectory)
    outputs.file(outputFile)
    doLast {
        ZipOutputStream(outputFile.get().asFile).use { output ->
            output.putDirectoryDuplicate(compileJavaTask.get().destinationDirectory.asFile.get())
            output.putDirectoryDuplicate(compileKotlinTask.get().destinationDirectory.asFile.get())
        }
    }
}
artifacts.add(duplicateEntityJarConfig.name, duplicateEntityJarTask)

fun ZipOutputStream.putDirectoryDuplicate(root: File) {
    fun putEntry(name: String) {
        putNextEntry(ZipEntry(name).apply {
            method = ZipEntry.DEFLATED
        })
    }
    root.walk().forEach { file ->
        if (file == root) return@forEach
        val path = file.relativeTo(root).path.replace(File.pathSeparatorChar, '/')
        if (file.isDirectory) {
            val dirPath = "$path/"
            putEntry(dirPath)
            putEntry(dirPath)
        } else {
            val buffer = file.readBytes()
            putEntry(path)
            write(buffer)
            putEntry(path)
            write(buffer)
        }
    }
}
