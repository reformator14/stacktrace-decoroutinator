@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.api

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.ModuleNode

interface ProcessingClass {
    val node: ClassNode
    fun markModified()
    fun delete()
}

interface ProcessingModule {
    val node: ModuleNode
    fun markModified()
}

interface ProcessingDirectory {
    val classes: Sequence<ProcessingClass>
    val module: ProcessingModule?
}

interface BytecodeProcessorContext {
    interface Key<T: Any> {
        val id: String
        val default: T
        fun merge(value1: T, value2: T): T
        fun isEmpty(value: T): Boolean
    }

    operator fun <T: Any> get(key: Key<T>): T

    fun <T: Any> merge(key: Key<T>, value: T): T
}

@JvmInline
value class BytecodeProcessorContextImpl private constructor(
    val values: MutableMap<String, Any>
): BytecodeProcessorContext {
    constructor(): this(hashMapOf())

    @Suppress("UNCHECKED_CAST")
    override fun <T: Any> get(key: BytecodeProcessorContext.Key<T>): T =
        values[key.id]?.let { it as T } ?: key.default

    @Suppress("UNCHECKED_CAST")
    override fun <T: Any> merge(key: BytecodeProcessorContext.Key<T>, value: T): T {
        val newValue = key.merge(get(key), value)
        if (key.isEmpty(newValue)) {
            values.remove(key.id)
        } else {
            values[key.id] = newValue
        }
        return newValue
    }
}

fun interface Processor {
    fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext)
}
