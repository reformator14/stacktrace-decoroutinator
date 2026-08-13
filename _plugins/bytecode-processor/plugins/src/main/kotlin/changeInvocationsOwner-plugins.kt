@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.ChangeInvocationsOwner
import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContext
import dev.reformator.bytecodeprocessor.api.ProcessingDirectory
import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import dev.reformator.bytecodeprocessor.plugins.internal.internalName
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

object ChangeInvocationsOwnerProcessor: Processor {
    object ContextKey: BytecodeProcessorContext.Key<Map<Key, String>> {
        override val id: String
            get() = "ownerInternalNamesByMethod"
        override val default: Map<Key, String>
            get() = emptyMap()
        override fun merge(value1: Map<Key, String>, value2: Map<Key, String>): Map<Key, String> {
            value1.forEach { (key, value) ->
                if (key in value2 && value2[key] != value) {
                    error("different owner internal names for methods [$key]: [$value] and [${value2[key]}]")
                }
            }
            return value1 + value2
        }
        override fun isEmpty(value: Map<Key, String>): Boolean =
            value.isEmpty()
    }

    data class Key(
        val ownerInternalName: String,
        val name: String,
        val descriptor: String
    ) {
        constructor(ownerInternalName: String, method: MethodNode): this(
            ownerInternalName = ownerInternalName,
            name = method.name,
            descriptor = method.desc
        )
    }

    override fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext) {
        val values = directory.classes.flatMap { processingClass ->
            val methodsToDelete: MutableCollection<MethodNode> = mutableListOf()
            val list = processingClass.node.methods.orEmpty().mapNotNull { method ->
                val annotation = method.invisibleAnnotations.find(ChangeInvocationsOwner::class.java)
                    ?: return@mapNotNull null

                val toParameter = annotation.getParameter(ChangeInvocationsOwner::to.name) as Type?
                val toNameParameter = annotation.getParameter(ChangeInvocationsOwner::toName.name) as String?
                if (toParameter != null && toNameParameter != null) {
                    error("both parameters [${ChangeInvocationsOwner::to.name}] and [${ChangeInvocationsOwner::toName.name}] were set")
                }

                val toInternalName: String = when {
                    toParameter != null -> toParameter.internalName
                    toNameParameter != null -> toNameParameter.internalName
                    else -> error("both parameters [${ChangeInvocationsOwner::to.name}] and [${ChangeInvocationsOwner::toName.name}] weren't set")
                }
                require(toInternalName != processingClass.node.name)

                val deleteAfterChanging =
                    annotation.getParameter(ChangeInvocationsOwner::deleteAfterChanging.name) as Boolean? ?: true

                val key = Key(
                    ownerInternalName = processingClass.node.name,
                    method = method
                )

                if (deleteAfterChanging) {
                    methodsToDelete.add(method)
                } else {
                    method.invisibleAnnotations = method.invisibleAnnotations.filter { it != annotation }
                }
                processingClass.markModified()

                key to toInternalName
            }
            if (methodsToDelete.isNotEmpty()) {
                processingClass.node.methods = processingClass.node.methods.filter { it !in methodsToDelete }
            }
            list
        }.toMap()
        context.merge(ContextKey, values)

        directory.classes.forEach { processingClass ->
            processingClass.node.methods?.forEach { method ->
                method.instructions?.forEach {
                    if (it is MethodInsnNode) {
                        val key = Key(
                            ownerInternalName = it.owner,
                            name = it.name,
                            descriptor = it.desc
                        )
                        val newOwnerInternalName = context[ContextKey][key]
                        if (newOwnerInternalName != null) {
                            it.owner = newOwnerInternalName
                            processingClass.markModified()
                        }
                    }
                }
            }
        }
    }
}
