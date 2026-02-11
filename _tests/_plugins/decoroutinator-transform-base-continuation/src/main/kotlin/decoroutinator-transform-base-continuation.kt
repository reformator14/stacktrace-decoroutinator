@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.decoroutinatortransformbasecontinuation

import dev.reformator.stacktracedecoroutinator.classtransformer.internal.transformClassBody
import dev.reformator.stacktracedecoroutinator.intrinsics.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.intrinsics.PROVIDER_MODULE_NAME
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.ModuleNode
import org.objectweb.asm.tree.ModuleRequireNode
import java.io.InputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

val decoroutinatorTransformedBaseContinuationAttribute: Attribute<Boolean> = Attribute.of(
    "dev.reformator.decoroutinatortransformbasecontinuation.transformedBaseContinuation",
    Boolean::class.javaObjectType
)

@Suppress("unused")
internal class DecoroutinatorTransformBaseContinuationPlugin: Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        dependencies.attributesSchema.attribute(decoroutinatorTransformedBaseContinuationAttribute)
        dependencies.artifactTypes.getByName("jar") { artifact ->
            artifact.attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, false)
        }
        dependencies.registerTransform(DecoroutinatorTransformBaseContinuationAction::class.java) { spec ->
            spec.from.attribute(decoroutinatorTransformedBaseContinuationAttribute, false)
            spec.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
            spec.to.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
            spec.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        }
    }
}

internal abstract class DecoroutinatorTransformBaseContinuationAction: TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val file = inputArtifact.get().asFile
        if (file.name.startsWith("kotlin-stdlib-")) {
            JarOutputStream(outputs.file(file.name.addVariant("decoroutinator-transformed-base-continuation")).outputStream()).use { output ->
                JarFile(file).use { input ->
                    input.entries().asSequence().forEach { entry ->
                        output.putNextEntry(ZipEntry(entry.name).apply {
                            method = ZipEntry.DEFLATED
                        })
                        if (entry.name == baseContinuationEntryName) {
                            output.write(input.getInputStream(entry).use {
                                transformClassBody(
                                    classBody = it,
                                    skipSpecMethods = false,
                                    metadataResolver = { error("no need") }
                                ).updatedBody!!
                            })
                        } else if (entry.name.endsWith("/module-info.class")) {
                            output.write(input.getInputStream(entry).use { input ->
                                val moduleNode = readModuleInfo(input)
                                moduleNode.module.addRequiresModule(PROVIDER_MODULE_NAME)
                                moduleNode.getClassBody()
                            })
                        } else if (!entry.isDirectory) {
                            input.getInputStream(entry).use { it.copyTo(output) }
                        }
                        output.closeEntry()
                    }
                }
            }
        } else {
            outputs.file(inputArtifact)
        }
    }
}

private val baseContinuationEntryName = BASE_CONTINUATION_CLASS_NAME.replace('.', '/') + ".class"

private fun readModuleInfo(body: InputStream): ClassNode {
    val classNode = readClassNode(body)
    require(classNode.module != null)
    return classNode
}

private fun readClassNode(classBody: InputStream): ClassNode {
    val classReader = ClassReader(classBody)
    val classNode = ClassNode(Opcodes.ASM9)
    classReader.accept(classNode, ClassReader.SKIP_CODE)
    return classNode
}

private fun ModuleNode.addRequiresModule(moduleName: String) {
    val requires = this.requires.orEmpty()
    if (requires.any { it.module == moduleName }) return
    this.requires = requires + ModuleRequireNode(moduleName, Opcodes.ACC_SYNTHETIC, null)
}

private fun ClassNode.getClassBody(): ByteArray {
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    accept(writer)
    return writer.toByteArray()
}

private fun String.addVariant(variant: String): String {
    val suffix = lastIndexOf('.').let { index ->
        if (index == -1) "" else substring(index)
    }
    return "${removeSuffix(suffix)}-$variant$suffix"
}
