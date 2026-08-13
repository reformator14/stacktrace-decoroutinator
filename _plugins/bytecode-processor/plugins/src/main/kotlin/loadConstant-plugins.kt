@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.ClassNameConstant
import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContext
import dev.reformator.bytecodeprocessor.api.ProcessingDirectory
import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.intrinsics.MethodNameConstant
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import dev.reformator.bytecodeprocessor.plugins.internal.isStatic
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

private val stringMethodDesc = "()${Type.getDescriptor(String::class.java)}"

object LoadConstantProcessor: Processor {
    object KeyValueContextKey: BytecodeProcessorContext.Key<Map<String, String>> {
        override val id: String
            get() = "constantKeyValues"
        override val default: Map<String, String>
            get() = emptyMap()
        override fun merge(value1: Map<String, String>, value2: Map<String, String>): Map<String, String> {
            value1.forEach { (key, value) ->
                if (key in value2 && value2[key] != value) {
                    error("different constant values for key [$key]: [$value] and [${value2[key]}]")
                }
            }
            return value1 + value2
        }
        override fun isEmpty(value: Map<String, String>): Boolean =
            value.isEmpty()
    }

    object MethodKeyContextKey: BytecodeProcessorContext.Key<Map<MethodKey, String>> {
        override val id: String
            get() = "constantMethodKeys"
        override val default: Map<MethodKey, String>
            get() = emptyMap()
        override fun merge(value1: Map<MethodKey, String>, value2: Map<MethodKey, String>): Map<MethodKey, String> {
            value1.forEach { (key, value) ->
                if (key in value2 && value2[key] != value) {
                    error("different constant values for method [$key]: [$value] and [${value2[key]}]")
                }
            }
            return value1 + value2
        }
        override fun isEmpty(value: Map<MethodKey, String>): Boolean =
            value.isEmpty()
    }

    data class MethodKey(
        val internalClassName: String,
        val methodName: String
    )

    fun addValues(context: BytecodeProcessorContext, valuesByKeys: Map<String, String>) {
        context.merge(KeyValueContextKey, valuesByKeys)
    }

    fun getValue(context: BytecodeProcessorContext, key: String): String? {
        return context[KeyValueContextKey][key]
    }

    override fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext) {
        directory.classes.forEach { processingClass ->
            val annotation = processingClass.node.invisibleAnnotations.find(ClassNameConstant::class.java)
                ?: return@forEach

            val key = annotation.getParameter(ClassNameConstant::key.name) as String
            context.merge(
                key = KeyValueContextKey,
                value = mapOf(key to Type.getObjectType(processingClass.node.name).className)
            )

            processingClass.node.invisibleAnnotations =
                processingClass.node.invisibleAnnotations.filter { it != annotation }
            processingClass.markModified()
        }

        directory.classes.forEach { processingClass ->
            processingClass.node.methods?.forEach forEachMethod@{ method ->
                val annotation = method.invisibleAnnotations.find(MethodNameConstant::class.java)
                    ?: return@forEachMethod

                val key = annotation.getParameter(MethodNameConstant::key.name) as String
                context.merge(
                    key = KeyValueContextKey,
                    value = mapOf(key to method.name)
                )

                method.invisibleAnnotations = method.invisibleAnnotations.filter { it != annotation }
                processingClass.markModified()
            }
        }

        val keys = directory.classes.flatMap { processingClass ->
            val methodsToDelete = mutableListOf<MethodNode>()

            val list = processingClass.node.methods.orEmpty().mapNotNull { method ->
                val annotation = method.invisibleAnnotations.find(LoadConstant::class.java)
                    ?: return@mapNotNull null

                require(method.isStatic)
                require(method.desc == stringMethodDesc)

                val key = annotation.getParameter(LoadConstant::key.name) as String

                val deleteAfterChanging =
                    annotation.getParameter(LoadConstant::deleteAfterModification.name) as Boolean? ?: true

                if (deleteAfterChanging) {
                    methodsToDelete.add(method)
                } else {
                    method.invisibleAnnotations = method.invisibleAnnotations.filter { it != annotation }
                }

                processingClass.markModified()

                MethodKey(
                    internalClassName = processingClass.node.name,
                    methodName = method.name
                ) to key
            }

            if (methodsToDelete.isNotEmpty()) {
                processingClass.node.methods = processingClass.node.methods.filter { it !in methodsToDelete }
            }

            list
        }.toMap()
        context.merge(MethodKeyContextKey, keys)

        directory.classes.forEach { clazz ->
            clazz.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (
                        instruction is MethodInsnNode
                        && instruction.opcode == Opcodes.INVOKESTATIC
                        && instruction.desc == stringMethodDesc
                    ) {
                        val key = MethodKey(
                            internalClassName = instruction.owner,
                            methodName = instruction.name
                        )
                        val valueKey = context[MethodKeyContextKey][key]
                        if (valueKey != null) {
                            val value = context[KeyValueContextKey][valueKey]
                                ?: error("not found value for key [$valueKey] calling " +
                                        "method [${instruction.owner}#${instruction.name}] " +
                                        "in method [${clazz.node.name}#${method.name}${method.desc}]")
                            method.instructions.set(instruction, LdcInsnNode(value))
                            clazz.markModified()
                        }
                    }
                }
            }
        }
    }
}
