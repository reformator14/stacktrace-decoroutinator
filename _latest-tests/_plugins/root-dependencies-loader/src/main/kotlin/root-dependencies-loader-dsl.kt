@file:Suppress("PackageDirectoryMismatch")

package org.gradle.kotlin.dsl

import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.DependenciesConfiguration
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.EXTENSION_NAME
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.Extension
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.applyTo
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.buildDependenciesConfiguration
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.getRootDependenciesBuildLibsClassesJar
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.getRootDependenciesClassesJar
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.libsCoroutinesCoreBuild
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.libsJunit4
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.libsJupiterApi
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.invoke
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.libsAsmUtils
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.libsByteBuddyAgent
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.libsJacksonCore
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.libsKotlinMetadataJvm
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.libsJacksonKotlin
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.libsKotlinGradlePluginApi
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.libsKotlinLoggingJvm
import dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader.libsKtorIoJvm
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler

fun Project.rootDependenciesLoader(configurer: Extension.() -> Unit) {
    extensions.configure<Extension>(EXTENSION_NAME) { it.configurer() }
}

val Project.rootDependenciesLoader: Extension
    get() = extensions.getByName(EXTENSION_NAME) as Extension

val Project.bytecodeProcessorIntrinsics: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar(
            "_plugins", "bytecode-processor", "intrinsics"
        ))
    }

val Project.bytecodeProcessorApi: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(libsAsmUtils)
        addApi(getRootDependenciesBuildLibsClassesJar(
            "_plugins", "bytecode-processor", "api"
        ))
    }

val Project.bytecodeProcessorPlugins: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar(
            "_plugins", "bytecode-processor", "plugins"
        ))
        addRuntime(bytecodeProcessorApi)
        addRuntime(bytecodeProcessorIntrinsics)
    }

val Project.bytecodeProcessorGradlePlugin: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(bytecodeProcessorPlugins)
        addApi(getRootDependenciesBuildLibsClassesJar(
            "_plugins", "bytecode-processor", "gradle-plugin"
        ))
        addRuntime(bytecodeProcessorApi)
        addRuntime(libsKotlinGradlePluginApi)
        addRuntime(libsJacksonCore)
        addRuntime(libsJacksonKotlin)
    }

val Project.decoroutinatorProvider: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar("provider"))
    }

val Project.decoroutinatorRuntimeSettings: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar("runtime-settings"))
    }

val Project.decoroutinatorCommon: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar("common"))
        addApi(decoroutinatorRuntimeSettings)
        addRuntime(decoroutinatorProvider)
    }

val Project.decoroutinatorTestsDuplicateEntityJar: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesClassesJar(
            "tests", "duplicate-entity-jar", "build"
        ))
    }

val Project.decoroutinatorTests: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar("tests"))
        addRuntime(libsJupiterApi)
        addRuntime(libsJunit4)
        addRuntime(libsCoroutinesCoreBuild)
        addRuntime(libsKtorIoJvm { dependency ->
            dependency.exclude(mapOf(
                "group" to "org.jetbrains.kotlinx",
                "module" to "kotlinx-coroutines-jdk8"
            ))
        })
        addRuntime(decoroutinatorTestsDuplicateEntityJar)
    }

val Project.decoroutinatorTestsMethodWithSpacesTests: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar(
            "tests", "methods-with-spaces-tests"
        ))
        addApi(decoroutinatorTests)
        addRuntime(libsJupiterApi)
        addRuntime(libsCoroutinesCoreBuild)
    }

val Project.decoroutinatorSpecMethodBuilder: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar("spec-method-builder"))
        addRuntime(libsAsmUtils)
        addRuntime(decoroutinatorProvider)
    }

val Project.decoroutinatorClassTransformer: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar("class-transformer"))
        addRuntime(libsAsmUtils)
        addRuntime(libsKotlinMetadataJvm)
        addRuntime(decoroutinatorProvider)
        addRuntime(decoroutinatorSpecMethodBuilder)
    }

val Project.decoroutinatorMhInvoker: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar("mh-invoker"))
        addRuntime(decoroutinatorProvider)
        addRuntime(decoroutinatorCommon)
    }

val Project.decoroutinatorGeneratorJvm: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar("generator-jvm"))
        addApi(decoroutinatorCommon)
        addRuntime(libsAsmUtils)
        addRuntime(decoroutinatorProvider)
        addRuntime(decoroutinatorSpecMethodBuilder)
    }

val Project.decoroutinatorJvmAgentCommon: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar("jvm-agent-common"))
        addRuntime(decoroutinatorMhInvoker)
        addRuntime(decoroutinatorGeneratorJvm)
        addRuntime(decoroutinatorProvider)
        addRuntime(decoroutinatorRuntimeSettings)
        addRuntime(decoroutinatorClassTransformer)
        addRuntime(libsAsmUtils)
    }

val Project.decoroutinatorJvm: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar("jvm"))
        addApi(decoroutinatorCommon)
        addRuntime(decoroutinatorJvmAgentCommon)
        addRuntime(decoroutinatorProvider)
        addRuntime(libsByteBuddyAgent)
    }

val Project.decoroutinatorGradlePlugin: DependenciesConfiguration
    get() = buildDependenciesConfiguration {
        addApi(getRootDependenciesBuildLibsClassesJar("gradle-plugin"))
        addRuntime(decoroutinatorClassTransformer)
        addRuntime(decoroutinatorRuntimeSettings)
        addRuntime(decoroutinatorProvider)
        addRuntime(libsKotlinLoggingJvm)
        addRuntime(libsKotlinGradlePluginApi)
        addRuntime(libsAsmUtils)
    }

fun DependencyHandler.compileOnly(dependencies: DependenciesConfiguration) {
    dependencies.api.applyTo(this, "compileOnly")
}

fun DependencyHandler.runtimeOnly(dependencies: DependenciesConfiguration) {
    dependencies.api.applyTo(this, "runtimeOnly")
    dependencies.runtime.applyTo(this, "runtimeOnly")
}

fun DependencyHandler.implementation(dependencies: DependenciesConfiguration) {
    dependencies.api.applyTo(this, "implementation")
    dependencies.runtime.applyTo(this, "runtimeOnly")
}

fun DependencyHandler.api(dependencies: DependenciesConfiguration) {
    dependencies.api.applyTo(this, "api")
    dependencies.runtime.applyTo(this, "runtimeOnly")
}

fun DependencyHandler.testImplementation(dependencies: DependenciesConfiguration) {
    dependencies.api.applyTo(this, "testImplementation")
    dependencies.runtime.applyTo(this, "testRuntimeOnly")
}

fun DependencyHandler.testRuntimeOnly(dependencies: DependenciesConfiguration) {
    dependencies.api.applyTo(this, "testRuntimeOnly")
    dependencies.runtime.applyTo(this, "testRuntimeOnly")
}
