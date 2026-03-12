@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.latesttests.rootdependenciesloader

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.rootDependenciesLoader

interface Extension {
    val rootPath: DirectoryProperty
}

@ConsistentCopyVisibility
data class DependenciesConfiguration internal constructor(
    internal val api: Dependencies = Dependencies.empty,
    internal val runtime: Dependencies = Dependencies.empty
)

internal const val EXTENSION_NAME = "rootDependenciesLoader"

internal data class Dependencies(
    val providers: Set<Provider<*>> = emptySet(),
    val files: FileCollection? = null,
    val providersWithActions: Set<ProviderWithAction> = emptySet()
) {
    companion object {
        val empty = Dependencies()
    }

    data class ProviderWithAction(
        val notation: Provider<*>,
        val configurationAction: Action<ExternalModuleDependency>
    )
}

@Suppress("unused")
internal class RootDependenciesLoaderPlugin: Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val extension = extensions.create(EXTENSION_NAME, Extension::class.java)
        extension.rootPath.set(rootProject.file(".."))
    }
}

internal class DependenciesConfigurationBuilder {
    var api: Dependencies = Dependencies.empty
    var runtime: Dependencies = Dependencies.empty

    fun addApi(dependencies: Dependencies) {
        api += dependencies
    }

    fun addRuntime(dependencies: Dependencies) {
        runtime += dependencies
    }

    fun addApi(configuration: DependenciesConfiguration) {
        api += configuration.api
        runtime += configuration.runtime
    }

    fun addRuntime(configuration: DependenciesConfiguration) {
        runtime += configuration.api + configuration.runtime
    }

    fun build(): DependenciesConfiguration =
        DependenciesConfiguration(api, runtime)
}

internal fun buildDependenciesConfiguration(
    builder: DependenciesConfigurationBuilder.() -> Unit
): DependenciesConfiguration =
    DependenciesConfigurationBuilder().apply(builder).build()

internal fun Project.getRootDependenciesClassesJar(vararg segments: String): Dependencies {
    val dir = segments.fold<String, Provider<Directory>>(
        initial = rootDependenciesLoader.rootPath
    ) { dir, segment ->
        dir.map { it.dir(segment) }
    }
    return fileTree(dir).classesJar
}

internal val FileCollection.classesJar: Dependencies
    get() = filter { file ->
        val name = file.name
        name.endsWith(".jar") && !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar")
    }.toDependencies

internal fun Project.getRootDependenciesBuildLibsClassesJar(
    vararg path: String
): Dependencies =
    getRootDependenciesClassesJar(*path, "build", "libs")

internal fun Project.getRootDependenciesFile(vararg segments: String): Dependencies {
    val dirSegments = segments.asSequence().take(segments.size - 1)
    val fileName = segments.last()
    val dir = dirSegments.fold<String, Provider<Directory>>(
        initial = rootDependenciesLoader.rootPath
    ) { dir, segment ->
        dir.map { it.dir(segment) }
    }
    return files(dir.map { it.file(fileName) }).toDependencies
}

internal val Project.libsAsmUtils: Dependencies
    get() = libs.reflectGet("asm").reflectGet("utils").toDependencies

internal val Project.libsKotlinGradlePluginApi: Dependencies
    get() = libs.reflectGet("kotlin").reflectGet("gradle")
        .reflectGet("plugin").reflectGet("api").toDependencies

internal val Project.libsJacksonCore: Dependencies
    get() = libs.reflectGet("jackson").reflectGet("core")
        .toDependencies

internal val Project.libsJacksonKotlin: Dependencies
    get() = libs.reflectGet("jackson").reflectGet("kotlin")
        .toDependencies

internal val Project.libsKtorIoJvm: Dependencies
    get() = libs.reflectGet("ktor").reflectGet("io")
        .reflectGet("jvm").toDependencies

internal val Project.libsByteBuddyAgent: Dependencies
    get() = libs.reflectGet("byte").reflectGet("buddy")
        .reflectGet("agent").toDependencies

internal val Project.libsKotlinLoggingJvm: Dependencies
    get() = libs.reflectGet("kotlin").reflectGet("logging")
        .reflectGet("jvm").toDependencies

internal val Project.libsKotlinMetadataJvm: Dependencies
    get() = libs.reflectGet("kotlin").reflectGet("metadata")
        .reflectGet("jvm").toDependencies

internal val Project.libsJupiterApi: Dependencies
    get() = libs.reflectGet("jupiter").reflectGet("api").toDependencies

internal val Project.libsJunit4: Dependencies
    get() = libs.reflectGet("junit4").toDependencies

internal val Project.libsCoroutinesCoreBuild: Dependencies
    get() = libs.reflectGet("coroutines").reflectGet("core")
        .reflectGet("build").toDependencies

internal operator fun Dependencies.invoke(
    configurationAction: Action<ExternalModuleDependency>
): Dependencies = Dependencies(
    files = files,
    providersWithActions = (
        providers.asSequence().map { provider ->
            Dependencies.ProviderWithAction(provider, configurationAction)
        } + providersWithActions.asSequence().map { providerWithAction ->
            Dependencies.ProviderWithAction(providerWithAction.notation) { provider ->
                providerWithAction.configurationAction.execute(provider)
                configurationAction.execute(provider)
            }
        }
    ).toSet()
)

internal operator fun DependenciesConfiguration.invoke(
    configurationAction: Action<ExternalModuleDependency>
): DependenciesConfiguration = DependenciesConfiguration(
    api = api(configurationAction),
    runtime = runtime(configurationAction)
)

internal fun Dependencies.applyTo(
    handler: DependencyHandler,
    configurationName: String
) {
    providers.forEach { handler.add(configurationName, it) }
    if (files != null) handler.add(configurationName, files)
    providersWithActions.forEach { dependency ->
        handler.addProvider(
            configurationName,
            dependency.notation,
            dependency.configurationAction
        )
    }
}

private operator fun Dependencies.plus(other: Dependencies): Dependencies =
    Dependencies(
        providers = providers + other.providers,
        files = when {
            files == null -> other.files
            other.files == null -> files
            else -> files + other.files
        },
        providersWithActions = providersWithActions + other.providersWithActions
    )

private val Project.libs: Any
    get() = extensions.getByName("libs")

private fun Any.reflectGet(propertyName: String): Any {
    val methodName = "get${propertyName[0].uppercase()}${propertyName.substring(1)}"
    return javaClass.getMethod(methodName).invoke(this)
}

private val Any.toDependencies: Dependencies
    get() = Dependencies(providers = setOf(this as Provider<*>))

private val FileCollection.toDependencies: Dependencies
    get() = Dependencies(files = this)
