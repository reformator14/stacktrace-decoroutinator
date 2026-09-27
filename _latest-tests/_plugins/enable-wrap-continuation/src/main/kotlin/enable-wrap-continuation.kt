@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.latesttests.enablewrapcontinuation

import dev.reformator.stacktracedecoroutinator.intrinsics.TAIL_CALL_ASYNC_STACK_TRACE_ENTRY_KT_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
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
import java.io.InputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

val enabledWrapContinuationAttribute: Attribute<Boolean> = Attribute.of(
    "dev.reformator.stacktracedecoroutinator.latesttests.enablewrapcontinuation.enabledWrapContinuation",
    Boolean::class.javaObjectType
)

/**
 * Makes kotlin-stdlib's `TailCallAsyncStackTraceEntryKt.wrapContinuation` actually wrap the continuation of
 * a tail-call suspend function - by replacing its (no-op) body with the body of `wrapContinuationReal`, the
 * same way the IntelliJ IDEA debugger does it.
 */
@Suppress("unused")
internal class EnableWrapContinuationPlugin: Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        dependencies.attributesSchema.attribute(enabledWrapContinuationAttribute)
        dependencies.artifactTypes.getByName("jar") { artifact ->
            artifact.attributes.attribute(enabledWrapContinuationAttribute, false)
        }
        dependencies.registerTransform(EnableWrapContinuationAction::class.java) { spec ->
            spec.from.attribute(enabledWrapContinuationAttribute, false)
            spec.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
            spec.to.attribute(enabledWrapContinuationAttribute, true)
            spec.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        }
    }
}

internal abstract class EnableWrapContinuationAction: TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val file = inputArtifact.get().asFile
        if (kotlinStdlibJarNameRegex.matches(file.name)) {
            var transformed = false
            JarOutputStream(outputs.file(file.name.addVariant("enabled-wrap-continuation")).outputStream()).use { output ->
                JarFile(file).use { input ->
                    input.entries().asSequence().forEach { entry ->
                        output.putNextEntry(ZipEntry(entry.name).apply {
                            method = ZipEntry.DEFLATED
                        })
                        if (entry.name == TAIL_CALL_ENTRY_NAME) {
                            output.write(input.getInputStream(entry).use { enableWrapContinuation(it) })
                            transformed = true
                        } else if (!entry.isDirectory) {
                            input.getInputStream(entry).use { it.copyTo(output) }
                        }
                        output.closeEntry()
                    }
                }
            }
            check(transformed) { "[$TAIL_CALL_ENTRY_NAME] was not found in [$file]" }
        } else {
            outputs.file(inputArtifact)
        }
    }
}

private val TAIL_CALL_ENTRY_NAME = "${TAIL_CALL_ASYNC_STACK_TRACE_ENTRY_KT_CLASS_NAME.internalName}.class"

// the main kotlin-stdlib jar only - not kotlin-stdlib-jdk7/-jdk8 or any other kotlin-stdlib-* artifact
private val kotlinStdlibJarNameRegex = Regex("kotlin-stdlib-\\d[^/]*\\.jar")

private fun enableWrapContinuation(classBody: InputStream): ByteArray {
    val classNode = ClassNode(Opcodes.ASM9)
    ClassReader(classBody).accept(classNode, 0)
    val wrapContinuation = classNode.methods.singleOrNull { it.name == "wrapContinuation" }
        ?: error("method [wrapContinuation] was not found")
    val wrapContinuationReal = classNode.methods.singleOrNull {
        it.name == "wrapContinuationReal" && it.desc == wrapContinuation.desc
    } ?: error("method [wrapContinuationReal] was not found")
    wrapContinuation.instructions = wrapContinuationReal.instructions
    wrapContinuation.tryCatchBlocks = wrapContinuationReal.tryCatchBlocks
    wrapContinuation.localVariables = wrapContinuationReal.localVariables
    wrapContinuation.maxStack = wrapContinuationReal.maxStack
    wrapContinuation.maxLocals = wrapContinuationReal.maxLocals
    // the instruction list is moved, not copied - an ASM InsnList can't belong to two methods at once
    classNode.methods.remove(wrapContinuationReal)
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classNode.accept(writer)
    return writer.toByteArray()
}

private fun String.addVariant(variant: String): String {
    val suffix = lastIndexOf('.').let { index ->
        if (index == -1) "" else substring(index)
    }
    return "${removeSuffix(suffix)}-$variant$suffix"
}
