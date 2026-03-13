@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DebugProbesEmbedderKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
import org.objectweb.asm.tree.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private val log = KotlinLogging.logger { }

private const val DEBUG_PROBES_CLASS_NAME = "kotlin.coroutines.jvm.internal.DebugProbesKt"
private const val DEBUG_PROBES_PROVIDER_CLASS_NAME = "kotlin.coroutines.jvm.internal.DecoroutinatorDebugProbesProvider"
private const val DEBUG_PROBES_IMPL_CLASS_NAME = "kotlinx.coroutines.debug.internal.DebugProbesImpl"
private const val DEBUG_PROBES_PROVIDER_IMPL_CLASS_NAME = "kotlinx.coroutines.debug.internal.DecoroutinatorDebugProbesProviderImpl"

private val ArtifactPath.isModuleInfo: Boolean
    get() = lastOrNull() == "module-info.class"

private inline fun Artifact.processArtifact(
    skipArtifact: () -> Unit,
    modifyArtifact: ((newArtifact: ArtifactBuilder) -> Unit) -> Unit
) {
    val debugProbesPath = DEBUG_PROBES_CLASS_NAME.className2ArtifactPath
    val debugProbesImplPath = DEBUG_PROBES_IMPL_CLASS_NAME.className2ArtifactPath
    if (containsFile(debugProbesPath)) {
        modifyArtifact { newArtifact ->
            walk(object: ArtifactWalker {
                override fun onFile(path: ArtifactPath, reader: () -> InputStream): Boolean {
                    if (path == debugProbesPath) {
                        return true
                    }
                    if (path.isModuleInfo) {
                        val moduleInfo = reader().use { tryReadModuleInfo(it) }
                        if (moduleInfo != null) {
                            val uses: MutableList<String> = moduleInfo.module.uses ?: run {
                                val uses = mutableListOf<String>()
                                moduleInfo.module.uses = uses
                                uses
                            }
                            uses.add(DEBUG_PROBES_PROVIDER_CLASS_NAME.internalName)
                            newArtifact.addFile(path, moduleInfo.classBody.inputStream())
                            return true
                        }
                    }
                    reader().use { newArtifact.addFile(path, it) }
                    return true
                }

                override fun onDirectory(path: ArtifactPath): Boolean {
                    newArtifact.addDirectory(path)
                    return true
                }
            })
            newArtifact.addJarClassesAndResources(embeddedDebugProbesStdlibJarBase64)
        }
    } else if (containsFile(debugProbesImplPath)) {
        modifyArtifact { newArtifact ->
            walk(object: ArtifactWalker {
                override fun onFile(path: ArtifactPath, reader: () -> InputStream): Boolean {
                    if (path.isModuleInfo) {
                        val moduleInfo = reader().use { tryReadModuleInfo(it) }
                        if (moduleInfo != null) {
                            val provides: MutableList<ModuleProvideNode> = moduleInfo.module.provides ?: run {
                                val provides = mutableListOf<ModuleProvideNode>()
                                moduleInfo.module.provides = provides
                                provides
                            }
                            provides.add(ModuleProvideNode(
                                DEBUG_PROBES_PROVIDER_CLASS_NAME.internalName,
                                listOf(DEBUG_PROBES_PROVIDER_IMPL_CLASS_NAME.internalName)
                            ))
                            moduleInfo.module.addRequiresModule("dev.reformator.stacktracedecoroutinator.runtimesettings")
                            newArtifact.addFile(path, moduleInfo.classBody.inputStream())
                            return true
                        }
                    }
                    reader().use { newArtifact.addFile(path, it) }
                    return true
                }

                override fun onDirectory(path: ArtifactPath): Boolean {
                    newArtifact.addDirectory(path)
                    return true
                }
            })
            newArtifact.addJarClassesAndResources(embeddedDebugProbesXcoroutinesJarBase64)
        }
    } else {
        skipArtifact()
    }
}

@CacheableTransform
abstract class DecoroutinatorEmbedDebugProbesAction: TransformAction<TransformParameters.None> {
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val root = inputArtifact.get().asFile
        log.debug { "trying embed DP for artifact [${root.absolutePath}]" }
        if (root.isFile) {
            log.debug { "artifact [${root.absolutePath}] is a file" }
            try {
                ZipFile(root).use { zip ->
                    ZipFileArtifact(zip).processArtifact(
                        skipArtifact = {
                            log.debug { "file artifact [${root.absolutePath}] was skipped" }
                            outputs.file(inputArtifact)
                        },
                        modifyArtifact = { builder ->
                            val newFile = outputs.file(root.name.addVariant("embed-dep-props"))
                            ZipOutputStream(newFile.outputStream()).use { output ->
                                builder(ZipArtifactBuilder(output))
                            }
                            log.debug { "file artifact [${root.absolutePath}] was transformed to [${newFile.absolutePath}]" }
                        }
                    )
                }
            } catch (e: IOException) {
                log.debug(e) { "Failed to read zip file [${root.absolutePath}]" }
                outputs.file(inputArtifact)
            }
        } else if (root.isDirectory) {
            log.debug { "artifact [${root.absolutePath}] is a directory" }
            DirectoryArtifact(root).processArtifact(
                skipArtifact = {
                    log.debug { "directory artifact [${root.absolutePath}] was skipped" }
                    outputs.dir(inputArtifact)
                },
                modifyArtifact = { builder ->
                    val newName = root.name + "-embed-dep-props"
                    val newRoot = outputs.dir(newName)
                    builder(DirectoryArtifact(newRoot))
                    log.debug { "directory artifact [${root.absolutePath}] was transformed to [${newRoot.absolutePath}]" }
                }
            )
        } else {
            log.debug { "artifact [${root.absolutePath}] does not exist" }
        }
    }
}

private val embeddedDebugProbesStdlibJarBase64: String
    @LoadConstant("embeddedDebugProbesStdlibJarBase64") get() = fail()
private val embeddedDebugProbesXcoroutinesJarBase64: String
    @LoadConstant("embeddedDebugProbesXcoroutinesJarBase64") get() = fail()
