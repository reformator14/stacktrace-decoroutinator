import com.android.build.gradle.internal.tasks.factory.dependsOn
import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.gradleplugin.BytecodeProcessorPluginExtension
import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor
import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import org.gradle.kotlin.dsl.the
import java.net.URLClassLoader

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

    implementation(decoroutinatorCommon)
    implementation(libs.jupiter.api)
    implementation(libs.junit4)
    implementation(libs.coroutines.core.latest)
    implementation(decoroutinatorTestsDuplicateEntityJar)

    api(decoroutinatorTests)
}

val baseOutputDir: Provider<Directory> = layout.buildDirectory.dir("tests-from-root")
val javaOutputDir: Provider<Directory> = baseOutputDir.map { it.dir("java") }
val kotlinOutputDir: Provider<Directory> = baseOutputDir.map { it.dir("kotlin") }
sourceSets.main.configure {
    java.srcDir(javaOutputDir)
    kotlin.srcDir(kotlinOutputDir)
}
val copyTestSourcesTask: TaskProvider<*> = tasks.register("copyTestSources") {
    val baseSourcesDir = rootDependenciesLoader.rootPath
        .dir("tests")
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
                        oldValue = "dev.reformator.stacktracedecoroutinator.tests",
                        newValue = "dev.reformator.stacktracedecoroutinator.latesttests.tests"
                    ).replace(
                        oldValue = "dev.reformator.stacktracedecoroutinator.latesttests.tests.duplicateentityjar",
                        newValue = "dev.reformator.stacktracedecoroutinator.tests.duplicateentityjar"
                    ))
                }
            }
        }
        copy(javaSourcesDir, javaOutputDirLocal)
        copy(kotlinSourcesDir, kotlinOutputDirLocal)
    }
}
tasks.compileJava.dependsOn(copyTestSourcesTask)
tasks.compileKotlin.dependsOn(copyTestSourcesTask)

sourceSets {
    main {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}

bytecodeProcessor {
    processors = listOf(
        GetCurrentFileNameProcessor,
        GetOwnerClassProcessor,
        LoadConstantProcessor
    )
}

// Resolved here, at project (script) scope, rather than inside the task registration lambda below -
// `the<T>()` there would resolve against the Task's own (empty) extension container, not the
// project's, since `this` inside tasks.register's lambda is the Task.
val bytecodeProcessorExecutionState = the<BytecodeProcessorPluginExtension>().executionState

val fillConstantProcessorTask: TaskProvider<*> = tasks.register("fillConstantProcessor") {
    val executionState = bytecodeProcessorExecutionState
    val rootDependenciesLoaderShadowJarFile = rootDependenciesLoader.rootPath.dir("tests")
        .map { it.dir("custom-loader") }
        .map { it.dir("build") }
        .map { it.dir("libs").asFile }
        .map { dir -> dir.listFiles { it.name.endsWith("-all.jar") }[0] }

    val bytecodeProcessorJarFile = rootDependenciesLoader.rootPath.dir("tests")
        .map { it.dir("bytecode-processor") }
        .map { it.dir("build") }
        .map { it.dir("libs").asFile }
        .map { dir -> dir.listFiles { file ->
            file.name.endsWith(".jar") &&
                !file.name.endsWith("-sources.jar") &&
                !file.name.endsWith("-javadoc.jar")
        } }
        .map { it[0] }

    doLast {
        val customLoaderJarUri = rootDependenciesLoaderShadowJarFile.get().toURI().toString()

        val addOpcodeTraceProcessor = URLClassLoader(
            arrayOf(bytecodeProcessorJarFile.get().toURI().toURL()),
            Processor::class.java.classLoader
        ).loadClass("dev.reformator.stacktracedecoroutinator.tests.bytecodeprocessor.AddOpcodeTraceProcessor")
            .getConstructor()
            .newInstance() as Processor

        executionState.initContext {
            LoadConstantProcessor.addValues(
                context = this,
                valuesByKeys = mapOf("customLoaderJarUri" to customLoaderJarUri)
            )
        }
        executionState.processors += addOpcodeTraceProcessor
    }
}
bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)
