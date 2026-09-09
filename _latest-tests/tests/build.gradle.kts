import com.android.build.gradle.internal.tasks.factory.dependsOn
import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor
import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
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

    // Root's own tests/build.gradle.kts only needs common at compile time (compileOnly), so
    // decoroutinatorTests correctly doesn't carry it - but unlike root, this module is also the
    // sole thing standing between common's real classes and android/gradle-plugin-tests' androidTest
    // runtime classpath: that module disables every automatic dependency-injection path the
    // stacktraceDecoroutinator Gradle plugin offers (addJvmRuntimeDependency/addAndroidRuntimeDependency
    // = false, all three *DependencyConfigurations.include = emptySet()) and relies entirely on
    // androidTestImplementation(project(":tests")) instead. A project dependency never leaks a
    // compileOnly dep to its consumers (compile or runtime), so narrowing this to compileOnly to
    // match root would silently break android/gradle-plugin-tests on-device. Keep as implementation.
    implementation(decoroutinatorCommon)

    // Unlike every other _latest-tests subproject, this module recompiles root's tests source
    // itself (copyTestSourcesTask, from root's tests/src/main) rather than just consuming root's
    // prebuilt tests jar as an opaque dependency - so it needs these on its own compile classpath
    // too, same as root's own tests/build.gradle.kts does. Relying on decoroutinatorTests alone
    // would NOT be enough even after it correctly mirrors root's dependencies: the
    // DependencyHandler.api/implementation(DependenciesConfiguration) wrappers below always apply a
    // configuration's `runtime` bucket as runtimeOnly at the consumer, never compile-visible,
    // regardless of which wrapper the consumer itself uses - and every implementation/runtimeOnly
    // root dependency lands in that `runtime` bucket by convention (see root-dependencies-loader-dsl.kt).
    // libs.ktor.io.jvm is the one exception: root itself only needs it at runtime (runtimeOnly), so
    // decoroutinatorTests's own addRuntime(...) - runtimeOnly here too - is already sufficient.
    implementation(libs.ktor.utils)
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

    outputs.dir(javaOutputDir)
    outputs.dir(kotlinOutputDir)

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
        copy(javaSourcesDir, javaOutputDir)
        copy(kotlinSourcesDir, kotlinOutputDir)
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

val fillConstantProcessorTask: TaskProvider<*> = tasks.register("fillConstantProcessor") {
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

        bytecodeProcessor {
            initContext {
                LoadConstantProcessor.addValues(
                    context = this,
                    valuesByKeys = mapOf("customLoaderJarUri" to customLoaderJarUri)
                )
            }
            processors += addOpcodeTraceProcessor
        }
    }
}
bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)
