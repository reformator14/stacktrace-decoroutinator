@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.api

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.ModuleNode
import java.io.File
import kotlin.collections.mutableListOf
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

fun File.applyBytecodeProcessors(
    processors: Collection<Processor>,
    context: BytecodeProcessorContext = BytecodeProcessorContextImpl(),
    skipUpdate: Boolean = false
) {
    val classes: List<ProcessingClassImpl>
    val module: ProcessingModuleImpl?
    readProcessingDirectory { readClasses, readModule ->
        classes = readClasses
        module = readModule
    }

    fun flushProcessingState(): Boolean {
        var isAnyClassProcessing = false
        classes.forEach { if (it.flushProcessingState()) isAnyClassProcessing = true }

        val isModuleProcessing = module != null && module.flushProcessingState()

        return isAnyClassProcessing || isModuleProcessing
    }

    val processingDirectory = object: ProcessingDirectory {
        override val classes: Sequence<ProcessingClass>
            get() = classes.asSequence().filter { it.state != ProcessingFileState.DELETED }

        override val module: ProcessingModule?
            get() = module
    }

    do {
        processors.forEach { it.process(processingDirectory, context) }
    } while (flushProcessingState())

    if (!skipUpdate) {
        classes.forEach { clazz ->
            when (clazz.state) {
                ProcessingFileState.DELETED -> require(clazz.file.delete())
                ProcessingFileState.MODIFIED -> {
                    val file = if (clazz.originalInternalName == clazz.node.name) {
                        clazz.file
                    } else {
                        require(clazz.file.delete())
                        val file = resolve(clazz.node.file)
                        file.parentFile.mkdirs()
                        file
                    }
                    clazz.node.writeTo(file)
                }
                else -> require(clazz.state == ProcessingFileState.UNMODIFIED)
            }
        }

        if (module != null) {
            if (module.state == ProcessingFileState.MODIFIED) {
                module.classNode.writeTo(module.file)
            } else {
                require(module.state == ProcessingFileState.UNMODIFIED)
            }
        }
    }
}

private class ProcessingClassImpl(
    val file: File,
    val originalInternalName: String,
    override val node: ClassNode
): ProcessingClass {
    var state = ProcessingFileState.UNMODIFIED

    override fun markModified() {
        state = state.modifyState
    }

    override fun delete() {
        state = state.deleteState
    }

    fun flushProcessingState(): Boolean =
        if (state.isProcessing) {
            state = state.finishProcessingState
            true
        } else {
            false
        }
}

private class ProcessingModuleImpl(
    val file: File,
    val classNode: ClassNode,
): ProcessingModule {
    var state = ProcessingFileState.UNMODIFIED

    override val node: ModuleNode
        get() = classNode.module

    override fun markModified() {
        state = state.modifyState
    }

    fun flushProcessingState(): Boolean =
        if (state.isProcessing) {
            state = state.finishProcessingState
            true
        } else {
            false
        }
}

private enum class ProcessingFileState {
    UNMODIFIED, MODIFIED, DELETED, MODIFYING, DELETING;

    val modifyState: ProcessingFileState
        get() = when (this) {
            UNMODIFIED, MODIFIED, MODIFYING -> MODIFYING
            DELETING -> error("Trying to modify deleting item")
            DELETED -> error("Something wrong. Trying to modify deleted item")
        }

    val deleteState: ProcessingFileState
        get() = when (this) {
            UNMODIFIED, MODIFIED -> DELETING
            MODIFYING -> error("Trying to delete modifying item")
            DELETING -> error("Trying to delete already deleting item")
            DELETED -> error("Something wrong. Trying to delete deleted item")
        }

    val isProcessing: Boolean
        get() = this == MODIFYING || this == DELETING

    val finishProcessingState: ProcessingFileState
        get() = when (this) {
            MODIFYING -> MODIFIED
            DELETING -> DELETED
            else -> error("Something wrong. State [$this] is not processing")
        }
}

private fun File.readClassNode(): ClassNode? =
    inputStream().use { input ->
        try {
            val classReader = ClassReader(input)
            val classNode = ClassNode(Opcodes.ASM9)
            classReader.accept(classNode, 0)
            classNode
        } catch (_: Exception) {
            null
        }
    }

private val ClassNode.file: File
    get() {
        val segments = name.split('/')
        return if (segments.size == 1) {
            File(segments[0] + ".class")
        } else if (segments.size > 1) {
            var path = File(segments[0])
            for (i in 1 until segments.lastIndex) {
                path = path.resolve(segments[i])
            }
            path.resolve(segments.last() + ".class")
        } else {
            error("no segments")
        }
    }

@OptIn(ExperimentalContracts::class)
private inline fun File.readProcessingDirectory(
    consumer: (classes: List<ProcessingClassImpl>, module: ProcessingModuleImpl?) -> Unit
) {
    contract { callsInPlace(consumer, InvocationKind.EXACTLY_ONCE) }

    require(isDirectory)

    val classes = mutableListOf<ProcessingClassImpl>()
    var module: ProcessingModuleImpl? = null

    walk().forEach { file ->
        if (!file.isFile || file.extension != "class") return@forEach

        val classNode = file.readClassNode() ?: return@forEach

        if (classNode.module != null) {
            if (module != null) {
                error("duplicate processing module")
            }
            module = ProcessingModuleImpl(
                file = file,
                classNode = classNode
            )
        } else {
            classes.add(ProcessingClassImpl(
                file = file,
                originalInternalName = classNode.name,
                node = classNode
            ))
        }
    }

    consumer(classes, module)
}

private fun ClassNode.writeTo(file: File) {
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    accept(writer)
    file.outputStream().use { it.write(writer.toByteArray()) }
}
