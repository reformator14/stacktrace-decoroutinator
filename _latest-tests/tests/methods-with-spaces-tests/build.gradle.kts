import com.android.build.gradle.internal.tasks.factory.dependsOn
import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor

plugins {
    alias(libs.plugins.root.dependencies.loader)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.bytecode.processor)
}

buildscript {
    dependencies {
        classpath("_plugins:build-dependencies")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(bytecodeProcessorIntrinsics)

    implementation(libs.jupiter.api)
    implementation(libs.coroutines.core.latest)

    api(project(":tests"))
    api(decoroutinatorTestsMethodWithSpacesTests)
}

sourceSets {
    main {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}

val baseOutputDir: Provider<Directory> = layout.buildDirectory.dir("methods-with-spaces-tests-from-root")
val javaOutputDir: Provider<Directory> = baseOutputDir.map { it.dir("java") }
val kotlinOutputDir: Provider<Directory> = baseOutputDir.map { it.dir("kotlin") }
sourceSets.main.configure {
    java.srcDir(javaOutputDir)
    kotlin.srcDir(kotlinOutputDir)
}
val copySourcesTask: TaskProvider<*> = tasks.register("copySources") {
    val baseSourcesDir = rootDependenciesLoader.rootPath
        .dir("tests")
        .map { it.dir("methods-with-spaces-tests") }
        .map { it.dir("src") }
        .map { it.dir("main") }
    val javaSourcesDir = baseSourcesDir.map { it.dir("java") }
    val kotlinSourcesDir = baseSourcesDir.map { it.dir("kotlin") }
    inputs.dir(javaSourcesDir)
    inputs.dir(kotlinSourcesDir)

    // Captured into locals rather than referenced directly inside doLast below: javaOutputDir/
    // kotlinOutputDir are top-level vals in this script, which the Kotlin script compiler turns into
    // members of the synthetic script class - referencing them from doLast (an execution-time closure
    // the configuration cache must serialize) would implicitly capture the script instance itself,
    // which the configuration cache rejects.
    val javaOutputDirLocal = javaOutputDir
    val kotlinOutputDirLocal = kotlinOutputDir

    outputs.dir(javaOutputDirLocal)
    outputs.dir(kotlinOutputDirLocal)

    doLast {
        fun copy(from: Provider<Directory>, to: Provider<Directory>) {
            val fromRoot = from.get().asFile
            val toRoot = to.get().asFile
            toRoot.deleteRecursively()
            fromRoot.walk().forEach { fromFile ->
                if (fromFile.isFile) {
                    val toFile = toRoot.resolve(fromFile.relativeTo(fromRoot))
                    toFile.parentFile.mkdirs()
                    toFile.writeText(fromFile.readText().replace(
                        oldValue = "dev.reformator.stacktracedecoroutinator.methodswithspacestests",
                        newValue = "dev.reformator.stacktracedecoroutinator.latesttests.methodswithspacestests"
                    ).replace(
                        oldValue = "dev.reformator.stacktracedecoroutinator.tests",
                        newValue = "dev.reformator.stacktracedecoroutinator.latesttests.tests"
                    ))
                }
            }
        }
        copy(javaSourcesDir, javaOutputDirLocal)
        copy(kotlinSourcesDir, kotlinOutputDirLocal)
    }
}
tasks.compileJava.dependsOn(copySourcesTask)
tasks.compileKotlin.dependsOn(copySourcesTask)

bytecodeProcessor {
    processors = listOf(
        GetCurrentFileNameProcessor,
        GetOwnerClassProcessor
    )
}
