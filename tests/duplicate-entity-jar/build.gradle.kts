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

// putDirectoryDuplicate is local to this task registration (not top-level in the script) so that
// duplicateEntityJarTask's doLast - an execution-time closure the configuration cache must be able to
// serialize - doesn't implicitly capture the build script object. A top-level fun in a .gradle.kts
// file is compiled as a member of the synthetic script class, so calling it from doLast would
// otherwise require serializing that script instance, which the configuration cache rejects.
val duplicateEntityJarTask: TaskProvider<*> = tasks.register("duplicateEntityJar") {
    val compileJavaTask = tasks.named<AbstractCompile>("compileJava")
    val compileKotlinTask = tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(compileJavaTask, compileKotlinTask)
    val outputFile = layout.buildDirectory.file("duplicate-entity.jar")
    val compileJavaDestinationDirectory = compileJavaTask.flatMap { it.destinationDirectory }
    val compileKotlinDestinationDirectory = compileKotlinTask.flatMap { it.destinationDirectory }
    inputs.dir(compileJavaDestinationDirectory)
    inputs.dir(compileKotlinDestinationDirectory)
    outputs.file(outputFile)

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

    doLast {
        ZipOutputStream(outputFile.get().asFile).use { output ->
            output.putDirectoryDuplicate(compileJavaDestinationDirectory.get().asFile)
            output.putDirectoryDuplicate(compileKotlinDestinationDirectory.get().asFile)
        }
    }
}
artifacts.add(duplicateEntityJarConfig.name, duplicateEntityJarTask)
