
@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("CommonGradlePluginKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import dev.reformator.stacktracedecoroutinator.intrinsics.PROVIDER_MODULE_NAME
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpecMethod
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidKeep
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidLegacyKeep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

private val log = KotlinLogging.logger { }

@Suppress("unused")
class DecoroutinatorPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        log.debug { "applying Decoroutinator plugin to [${target.name}]" }
        initGroovyDsl(target)
        with (target) {
            val pluginExtension = extensions.create(EXTENSION_NAME, DecoroutinatorPluginExtension::class.java)
            dependencies.attributesSchema.attribute(decoroutinatorTransformedStateAttribute)
            dependencies.attributesSchema.attribute(decoroutinatorEmbeddedDebugProbesAttribute)
            afterEvaluate { _ ->
                if (pluginExtension.enabled) {
                    pluginExtension.setupLowLevelConfig(target)
                    registerArtifactTransformers(
                        artifactTypes = pluginExtension.artifactTypes,
                        aarArtifactTypes = pluginExtension.artifactTypesForAarTransformation
                    )
                    registerEmbedDebugProbesTransformations(pluginExtension.artifactTypes)
                    registerArtifactTypeInitialAttributes(pluginExtension.artifactTypes)
                    addDependenciesToConfigurations(
                        regularDependencyConfigurationMatcher = pluginExtension.regularDependencyConfigurations.matcher,
                        androidDependencyConfigurationMatcher = pluginExtension.androidDependencyConfigurations.matcher,
                        jvmDependencyConfigurationMatcher = pluginExtension.jvmDependencyConfigurations.matcher
                    )
                    addAndroidGeneratorDependenciesToConfigurations(
                        configurationMatcher = pluginExtension.androidRuntimeDependencyConfigurations.matcher
                    )
                    addJvmGeneratorDependenciesToConfigurations(
                        configurationMatcher = pluginExtension.jvmRuntimeDependencyConfigurations.matcher
                    )
                    setConfigurationsTransformedAttributes(
                        regularConfigurationMatcher = pluginExtension.transformedClassesConfigurations.matcher,
                        skippingSpecMethodsConfigurationMatcher =
                            pluginExtension.transformedClassesSkippingSpecMethodsConfigurations.matcher
                    )
                    setConfigurationsEmbeddedDebugProbesAttributes(
                        configurationMatcher = pluginExtension.embeddedDebugProbesConfigurations.matcher
                    )
                    setCompileTasksInPlaceTransformations(
                        regularTasksMatcher = pluginExtension.tasks.matcher,
                        skippingSpecMethodsTasksMatcher = pluginExtension.tasksSkippingSpecMethods.matcher
                    )
                    unsetTransformedAttributesForAllProjectsConfigurationsOutgoingVariants(
                        artifactTypes = pluginExtension.artifactTypes
                    )
                    if (isAndroid) {
                        setGeneratingProguardFiles(pluginExtension.legacyAndroidCompatibility)
                    }
                } else {
                    log.debug { "Decoroutinator plugin is disabled" }
                }
            }
        }
    }
}

private val Project.isAndroid: Boolean
    get() = pluginManager.hasPlugin("com.android.base")

private val Project.isKmp: Boolean
    get() = pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")

private val Project.androidMinSdk: Int?
    get() {
        try {
            val ext = extensions.getByName("android") ?: return null
            val defaultConfig = ext.javaClass.getMethod("getDefaultConfig").invoke(ext) ?: return null
            return defaultConfig.javaClass.getMethod("getMinSdk").invoke(defaultConfig) as? Int?
        } catch (_: UnknownDomainObjectException) {
            return null
        } catch (_: ReflectiveOperationException) {
            return null
        }
    }

private fun DecoroutinatorPluginExtension.setupHighLevelConfig(project: Project) {
    _legacyAndroidCompatibility.updateIfNotSet {
        if (!project.isAndroid) {
            return@updateIfNotSet false
        }
        val minSdk = project.androidMinSdk
        minSdk != null && minSdk < 26
    }
    _embedDebugProbesForAndroidTest.updateIfNotSet {
        embedDebugProbesForAndroid
    }
}

private fun DecoroutinatorPluginExtension.setupLowLevelDependencyConfig(project: Project) {
    val isAndroid = project.isAndroid
    val isKmp = project.isKmp
    val androidTestConfigs = when {
        isKmp -> setOf("androidTestImplementation", "androidInstrumentedTestImplementation")
        isAndroid -> setOf("androidTestImplementation", "testImplementation")
        else -> emptySet()
    }
    val androidMainConfigs = when {
        isKmp -> setOf("androidMainImplementation")
        isAndroid -> setOf("implementation")
        else -> emptySet()
    }
    val jvmTestConfigs = when {
        isKmp -> setOf("desktopTestImplementation", "androidUnitTestImplementation")
        isAndroid -> setOf(".*UnitTestImplementation", "unitTestImplementation")
        else -> setOf("testImplementation")
    }
    val jvmMainConfigs = when {
        isKmp -> setOf("desktopMainImplementation", "androidUnitTestImplementation")
        isAndroid -> setOf(".*UnitTestImplementation", "unitTestImplementation")
        else -> setOf("implementation")
    }
    val androidConfigs = if (androidTestsOnly) androidTestConfigs else androidMainConfigs
    val jvmConfigs = if (jvmTestsOnly) jvmTestConfigs else jvmMainConfigs
    regularDependencyConfigurations._include.updateIfNotSet {
        if (legacyAndroidCompatibility) emptySet() else androidConfigs + jvmConfigs
    }
    androidDependencyConfigurations._include.updateIfNotSet {
        androidConfigs
    }
    jvmDependencyConfigurations._include.updateIfNotSet {
        jvmConfigs
    }
    androidRuntimeDependencyConfigurations._include.updateIfNotSet {
        if (addAndroidRuntimeDependency) {
            androidConfigs
        } else {
            emptySet()
        }
    }
    jvmRuntimeDependencyConfigurations._include.updateIfNotSet {
        if (addJvmRuntimeDependency) {
            jvmConfigs
        } else {
            emptySet()
        }
    }
}

private fun DecoroutinatorPluginExtension.setupLowLevelRuntimeTransformedClassesConfig(project: Project) {
    val isAndroid = project.isAndroid
    val isKmp = project.isKmp
    val androidConfigurations = when {
        isKmp -> {
            if (androidTestsOnly) {
                setOf(".*AndroidTestRuntimeClasspath", "androidTestRuntimeClasspath")
            } else {
                setOf(
                    "androidDebugRuntimeClasspath",
                    "androidReleaseRuntimeClasspath",
                    ".*AndroidTestRuntimeClasspath",
                    "androidTestRuntimeClasspath",
                    "debugRuntimeClasspath",
                    "releaseRuntimeClasspath"
                )
            }
        }
        isAndroid -> {
            if (androidTestsOnly) {
                setOf(".*AndroidTestRuntimeClasspath", "androidTestRuntimeClasspath")
            } else {
                setOf(
                    ".*RuntimeClasspath",
                    "runtimeClasspath",
                    "debugRuntimeClasspath",
                    "releaseRuntimeClasspath"
                )
            }
        }
        else -> emptySet()
    }
    val jvmConfigurations = when {
        isKmp -> {
            if (jvmTestsOnly) {
                setOf(".*UnitTestRuntimeClasspath", "desktopTestRuntimeClasspath")
            } else {
                setOf(".*UnitTestRuntimeClasspath", "desktopRuntimeClasspath", "desktopMainRuntimeClasspath")
            }
        }
        isAndroid -> setOf(".*UnitTestRuntimeClasspath", "unitTestRuntimeClasspath")
        else -> {
            if (jvmTestsOnly) {
                setOf(".*TestRuntimeClasspath", "testRuntimeClasspath")
            } else {
                setOf(".*RuntimeClasspath", "runtimeClasspath")
            }
        }
    }
    transformedClassesConfigurations._include.updateIfNotSet {
        if (legacyAndroidCompatibility) {
            emptySet()
        } else {
            androidConfigurations + jvmConfigurations
        }
    }
    transformedClassesSkippingSpecMethodsConfigurations._include.updateIfNotSet {
        if (legacyAndroidCompatibility) {
            androidConfigurations + jvmConfigurations
        } else {
            emptySet()
        }
    }
}

private fun DecoroutinatorPluginExtension.setupLowLevelEmbeddedDebugProbesConfigurations(project: Project) {
    val isKmp = project.isKmp
    val testConfigs = when {
        isKmp -> setOf(".*AndroidTestRuntimeClasspath", "androidTestRuntimeClasspath")
        else -> setOf(".*AndroidTestRuntimeClasspath", "androidTestRuntimeClasspath")
    }
    val nonTestConfigs = when {
        isKmp -> {
            setOf(
                "androidDebugRuntimeClasspath",
                "androidReleaseRuntimeClasspath",
                ".*AndroidTestRuntimeClasspath",
                "androidTestRuntimeClasspath",
            )
        }
        else -> setOf(".*RuntimeClasspath", "runtimeClasspath")
    }
    embeddedDebugProbesConfigurations._include.updateIfNotSet {
        when {
            embedDebugProbesForAndroid -> nonTestConfigs
            embedDebugProbesForAndroidTest -> testConfigs
            else -> emptySet()
        }
    }
}

private fun DecoroutinatorPluginExtension.setupLowLevelCompileTransformedClassesConfig(project: Project) {
    val isAndroid = project.isAndroid
    val isKmp = project.isKmp
    val androidConfigurations = when {
        isKmp -> {
            if (androidTestsOnly) {
                setOf(".*AndroidTestCompileClasspath", "androidTestCompileClasspath")
            } else {
                setOf(
                    "androidDebugCompileClasspath",
                    "androidReleaseCompileClasspath",
                    ".*AndroidTestCompileClasspath",
                    "androidTestCompileClasspath",
                )
            }
        }
        isAndroid -> {
            if (androidTestsOnly) {
                setOf(".*AndroidTestCompileClasspath", "androidTestCompileClasspath")
            } else {
                setOf(".*CompileClasspath", "compileClasspath")
            }
        }
        else -> emptySet()
    }
    val jvmConfigurations = when {
        isKmp -> {
            if (jvmTestsOnly) {
                setOf("android.*UnitTestCompileClasspath", "desktopTestCompileClasspath")
            } else {
                setOf("android.*UnitTestCompileClasspath", "desktopCompileClasspath")
            }
        }
        isAndroid -> setOf(".*UnitTestCompileClasspath", "unitTestCompileClasspath")
        else -> {
            if (jvmTestsOnly) {
                setOf(".*TestCompileClasspath", "testCompileClasspath")
            } else {
                setOf(".*CompileClasspath", "compileClasspath")
            }
        }
    }
    transformedClassesConfigurations._include.updateIfNotSet {
        if (legacyAndroidCompatibility) {
            it
        } else {
            it + androidConfigurations + jvmConfigurations
        }
    }
    transformedClassesSkippingSpecMethodsConfigurations._include.updateIfNotSet {
        if (legacyAndroidCompatibility) {
            it + androidConfigurations + jvmConfigurations
        } else {
            it
        }
    }
}

private fun DecoroutinatorPluginExtension.setupLowLevelTasksConfig(project: Project) {
    val taskPatterns = if (!androidTestsOnly && !jvmTestsOnly) {
        setOf(".*")
    } else {
        val isAndroid = project.isAndroid
        val isKmp = project.isKmp
        val androidTasks = when {
            isKmp && androidTestsOnly -> setOf(".*AndroidTest.*")
            isKmp -> setOf(".*Android.*", ".*android.*")
            isAndroid && androidTestsOnly -> setOf(".*Test.*")
            isAndroid -> setOf(".*")
            else -> emptySet()
        }
        val jvmTasks = when {
            isKmp && jvmTestsOnly -> setOf(".*DesktopTest.*")
            isKmp -> setOf(".*Desktop.*")
            !isAndroid && jvmTestsOnly -> setOf(".*Test.*")
            !isAndroid -> setOf(".*")
            else -> emptySet()
        }
        androidTasks + jvmTasks
    }
    tasks._include.updateIfNotSet {
        if (legacyAndroidCompatibility) emptySet() else taskPatterns
    }
    tasksSkippingSpecMethods._include.updateIfNotSet {
        if (legacyAndroidCompatibility) taskPatterns else emptySet()
    }
}

private fun DecoroutinatorPluginExtension.setupLowLevelConfig(project: Project) {
    setupHighLevelConfig(project)
    setupLowLevelDependencyConfig(project)
    setupLowLevelRuntimeTransformedClassesConfig(project)
    if (useTransformedClassesForCompilation) {
        setupLowLevelCompileTransformedClassesConfig(project)
    }
    setupLowLevelTasksConfig(project)
    setupLowLevelEmbeddedDebugProbesConfigurations(project)
}

private fun Project.registerArtifactTransformers(artifactTypes: List<String>, aarArtifactTypes: Collection<String>) {
    log.debug { "registering artifact transformers for types [$artifactTypes]" }
    artifactTypes.forEachIndexed { artifactTypeIndex, artifactType ->
        sequenceOf(true, false).forEach { skipSpecMethods ->
            fun getTransformedState(index: Int): String {
                val transformedState = if (skipSpecMethods) {
                    DecoroutinatorTransformedState.TRANSFORMED_SKIPPING_SPEC_METHODS
                } else {
                    DecoroutinatorTransformedState.TRANSFORMED
                }
                if (index == 0) return transformedState
                return "$transformedState|$artifactType|$index"
            }

            val transformationAction = if (artifactType in aarArtifactTypes) {
                DecoroutinatorAarTransformAction::class.java
            } else {
                DecoroutinatorTransformAction::class.java
            }

            dependencies.registerTransform(transformationAction) { transformation ->
                transformation.from.attribute(
                    ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                    artifactType
                )
                transformation.from.attribute(
                    decoroutinatorTransformedStateAttribute,
                    DecoroutinatorTransformedState.UNTRANSFORMED
                )
                transformation.to.attribute(
                    ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                    artifactType
                )
                transformation.to.attribute(
                    decoroutinatorTransformedStateAttribute,
                    getTransformedState(artifactTypeIndex)
                )
                transformation.parameters { it.skipSpecMethods.set(skipSpecMethods) }
            }

            (1 .. artifactTypeIndex).forEach { index ->
                dependencies.registerTransform(DecoroutinatorNoopTransformAction::class.java) { transformation ->
                    transformation.from.attribute(
                        ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                        artifactType
                    )
                    transformation.from.attribute(
                        decoroutinatorTransformedStateAttribute,
                        getTransformedState(index)
                    )
                    transformation.to.attribute(
                        ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                        artifactType
                    )
                    transformation.to.attribute(
                        decoroutinatorTransformedStateAttribute,
                        getTransformedState(index - 1)
                    )
                }
            }
        }
    }
}

private fun Project.registerEmbedDebugProbesTransformations(artifactTypes: List<String>) {
    artifactTypes.forEachIndexed { artifactTypeIndex, artifactType ->
        fun getTransformedState(index: Int): String =
            if (index == 0) {
                DecoroutinatorEmbeddedDebugProbesState.TRUE
            } else {
                "$artifactType|$index"
            }

        dependencies.registerTransform(DecoroutinatorEmbedDebugProbesAction::class.java) { transformation ->
            transformation.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
            transformation.from.attribute(
                decoroutinatorTransformedStateAttribute,
                DecoroutinatorTransformedState.UNTRANSFORMED
            )
            transformation.from.attribute(
                decoroutinatorEmbeddedDebugProbesAttribute,
                DecoroutinatorEmbeddedDebugProbesState.FALSE
            )
            transformation.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
            transformation.to.attribute(
                decoroutinatorTransformedStateAttribute,
                DecoroutinatorTransformedState.UNTRANSFORMED
            )
            transformation.to.attribute(
                decoroutinatorEmbeddedDebugProbesAttribute,
                getTransformedState(artifactTypeIndex)
            )
        }

        (1 .. artifactTypeIndex).forEach { index ->
            dependencies.registerTransform(DecoroutinatorNoopTransformAction::class.java) { transformation ->
                transformation.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
                transformation.from.attribute(
                    decoroutinatorTransformedStateAttribute,
                    DecoroutinatorTransformedState.UNTRANSFORMED
                )
                transformation.from.attribute(
                    decoroutinatorEmbeddedDebugProbesAttribute,
                    getTransformedState(index)
                )
                transformation.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
                transformation.to.attribute(
                    decoroutinatorTransformedStateAttribute,
                    DecoroutinatorTransformedState.UNTRANSFORMED
                )
                transformation.to.attribute(
                    decoroutinatorEmbeddedDebugProbesAttribute,
                    getTransformedState(index - 1)
                )
            }
        }
    }
}

private fun Project.registerArtifactTypeInitialAttributes(artifactTypes: Collection<String>) {
    artifactTypes.forEach { artifactType ->
        dependencies.artifactTypes.maybeCreate(artifactType).attributes
            .attribute(
                decoroutinatorTransformedStateAttribute,
                DecoroutinatorTransformedState.UNTRANSFORMED
            )
            .attribute(
                decoroutinatorEmbeddedDebugProbesAttribute,
                DecoroutinatorEmbeddedDebugProbesState.FALSE
            )
    }
}

private fun Project.addDependenciesToConfigurations(
    regularDependencyConfigurationMatcher: StringMatcher,
    androidDependencyConfigurationMatcher: StringMatcher,
    jvmDependencyConfigurationMatcher: StringMatcher
) {
    configurations.configureEach { config ->
        with (dependencies) {
            if (regularDependencyConfigurationMatcher.matches(config.name)) {
                add(config.name, decoroutinatorRegularMethodHandleInvoker())
                add(config.name, decoroutinatorCommon())
            } else {
                if (androidDependencyConfigurationMatcher.matches(config.name)) {
                    add(config.name, decoroutinatorAndroidMethodHandleInvoker())
                    add(config.name, decoroutinatorCommon())
                }
                if (jvmDependencyConfigurationMatcher.matches(config.name)) {
                    add(config.name, decoroutinatorJvmMethodHandleInvoker())
                    add(config.name, decoroutinatorCommon())
                }
            }
        }
    }
}

private fun Project.addAndroidGeneratorDependenciesToConfigurations(configurationMatcher: StringMatcher) {
    configurations.configureEach { config ->
        if (configurationMatcher.matches(config.name)) {
            with (dependencies) {
                add(config.name, decoroutinatorAndroidRuntime())
            }
        }
    }
}

private fun Project.addJvmGeneratorDependenciesToConfigurations(configurationMatcher: StringMatcher) {
    configurations.configureEach { config ->
        if (configurationMatcher.matches(config.name)) {
            with (dependencies) {
                add(config.name, decoroutinatorJvmRuntime())
            }
        }
    }
}

private fun Project.setConfigurationsTransformedAttributes(
    regularConfigurationMatcher: StringMatcher,
    skippingSpecMethodsConfigurationMatcher: StringMatcher
) {
    configurations.configureEach { config ->
        val state = when {
            regularConfigurationMatcher.matches(config.name) -> DecoroutinatorTransformedState.TRANSFORMED
            skippingSpecMethodsConfigurationMatcher.matches(config.name) ->
                DecoroutinatorTransformedState.TRANSFORMED_SKIPPING_SPEC_METHODS
            else -> null
        }
        if (state != null) {
            log.debug { "setting decoroutinatorTransformedStateAttribute for configuration [${config.name}] and state [$state]" }
            config.attributes.attribute(decoroutinatorTransformedStateAttribute, state)
        } else {
            log.debug { "skipping setting decoroutinatorTransformedStateAttribute for configuration [${config.name}]" }
        }
    }
}

private fun Project.setConfigurationsEmbeddedDebugProbesAttributes(configurationMatcher: StringMatcher) {
    configurations.configureEach { config ->
        if (configurationMatcher.matches(config.name)) {
            log.debug { "setting decoroutinatorEmbeddedDebugProbesAttribute for configuration [${config.name}]" }
            config.attributes.attribute(
                decoroutinatorEmbeddedDebugProbesAttribute,
                DecoroutinatorEmbeddedDebugProbesState.TRUE
            )
        } else {
            log.debug { "skipping setting decoroutinatorEmbeddedDebugProbesAttribute for configuration [${config.name}]" }
        }
    }
}

private fun Project.setCompileTasksInPlaceTransformations(
    regularTasksMatcher: StringMatcher,
    skippingSpecMethodsTasksMatcher: StringMatcher
) {
    tasks.withType(KotlinJvmCompile::class.java) { task ->
        val skipSpecMethods = when {
            regularTasksMatcher.matches(task.name) -> false
            skippingSpecMethodsTasksMatcher.matches(task.name) -> true
            else -> null
        }
        if (skipSpecMethods != null) {
            log.debug { "setting transforming classes action for task [${task.name}], skipSpecMethods = [$skipSpecMethods]" }
            task.doLast { _ ->
                val dir = task.destinationDirectory.get().asFile
                log.debug { "performing in-place transformation of a classes directory [${dir.absolutePath}], skipSpecMethods = [$skipSpecMethods]" }
                val artifact = DirectoryArtifact(dir)
                artifact.transform(
                    skipSpecMethods = skipSpecMethods,
                    onFile = { modified, path, body ->
                        if (modified) {
                            log.debug { "class file [$path] was transformed, skipSpecMethods = [$skipSpecMethods]" }
                            artifact.addFile(path, body)
                        }
                        true
                    },
                    onDirectory = { _ -> }
                )
            }
        } else {
            log.debug { "skipped transforming classes action for task [${task.name}]" }
        }
    }
    tasks.withType(AbstractCompile::class.java) { task ->
        if (regularTasksMatcher.matches(task.name) || skippingSpecMethodsTasksMatcher.matches(task.name)) {
            log.debug { "setting adding read provider module action for task [${task.name}]" }
            task.doLast { _ ->
                task.destinationDirectory.get().asFile.walk().forEach { path ->
                    if (path.isFile && path.name == MODULE_INFO_CLASS_NAME) {
                        val moduleNode = path.inputStream().use { tryReadModuleInfo(it) }
                        if (moduleNode != null) {
                            log.debug { "adding read provider module for file [${path.absolutePath}]" }
                            moduleNode.module.addRequiresModule(PROVIDER_MODULE_NAME)
                            path.outputStream().use { it.write(moduleNode.classBody) }
                        }
                    }
                }
            }
        } else {
            log.debug { "skipped adding read provider module action for task [${task.name}]" }
        }
    }
}

private fun Project.unsetTransformedAttributesForAllProjectsConfigurationsOutgoingVariants(
    artifactTypes: Collection<String>
) {
    val unsetTransformedAttributeAction =
        buildUnsetTransformedStateAttributeActionForAllConfigurationsOutgoingVariants(artifactTypes)
    rootProject.allprojects { project ->
        if (project.state.executed) {
            unsetTransformedAttributeAction.execute(project)
        } else {
            project.afterEvaluate(unsetTransformedAttributeAction)
        }
    }
}

private fun Project.setGeneratingProguardFiles(legacyAndroidCompatibility: Boolean) {
    val extractProguardFilesTask = tasks.findByName("extractProguardFiles")
    if (extractProguardFilesTask == null) {
        log.error { "Task [extractProguardFiles] was not found" }
    } else {
        val dir = decoroutinatorDir
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") val proguardRules = """
            # Decoroutinator ProGuard rules
            -keep,allowobfuscation @interface ${DecoroutinatorTransformed::class.java.name}
            -keep,allowobfuscation @interface ${DecoroutinatorSpecMethod::class.java.name}
            -keepattributes RuntimeVisibleAnnotations,LineNumberTable,SourceFile
            -keep,allowobfuscation,allowshrinking @${DecoroutinatorTransformed::class.java.name} class *
            -keepclassmembers,allowobfuscation @${DecoroutinatorTransformed::class.java.name} class * {
                static *(${DecoroutinatorSpec::class.java.name}, ${Object::class.java.name});
            }
            -keepclassmembers @${DecoroutinatorTransformed::class.java.name} class * {
                static <clinit>();
            }
            -keep,allowobfuscation interface ${DecoroutinatorSpec::class.java.name} { *; }
            -keep @${AndroidKeep::class.java.name} class * { *; }

        """.trimIndent()
        val legacyProguardRules = proguardRules + """
            -keep @${AndroidLegacyKeep::class.java.name} class * { *; }
            
        """.trimIndent()
        extractProguardFilesTask.doLast { _ ->
            dir.mkdirs()
            dir.resolve("android.pro").writeText(proguardRules)
            dir.resolve("android-legacy.pro").writeText(legacyProguardRules)
            val androidCurrentProguardRules = if (legacyAndroidCompatibility) {
                legacyProguardRules
            } else {
                proguardRules
            }
            dir.resolve(ANDROID_CURRENT_PROGUARD_RULES_FILE_NAME).writeText(androidCurrentProguardRules)
        }
    }
}
