@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContext
import dev.reformator.bytecodeprocessor.api.ProcessingClass
import dev.reformator.bytecodeprocessor.api.ProcessingDirectory
import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import dev.reformator.bytecodeprocessor.plugins.internal.internalName
import org.objectweb.asm.Handle
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

object ChangeClassNameProcessor: Processor {
    object ContextKey: BytecodeProcessorContext.Key<Map<String, String>> {
        override val id: String
            get() = "replacedClassInternalNamesByOriginalInternalName"
        override val default: Map<String, String>
            get() = emptyMap()
        override fun merge(value1: Map<String, String>, value2: Map<String, String>): Map<String, String> {
            value1.forEach { (key, value) ->
                if (key in value2 && value2[key] != value) {
                    error("different replaced class internal names for original internal name [$key]: [$value] and [${value2[key]}]")
                }
            }
            return value1 + value2
        }
        override fun isEmpty(value: Map<String, String>): Boolean =
            value.isEmpty()
    }

    fun add(context: BytecodeProcessorContext, replacedClassNamesByOriginalName: Map<String, String>) {
        context.merge(
            key = ContextKey,
            value = replacedClassNamesByOriginalName.asSequence().map { it.key.internalName to it.value.internalName }.toMap()
        )
    }

    override fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext) {
        val deletedClasses = mutableSetOf<ProcessingClass>()

        val values = directory.classes.mapNotNull { processingClass ->
            val annotation = processingClass.node.invisibleAnnotations.find(ChangeClassName::class.java)
                ?: return@mapNotNull null

            val toParameter = annotation.getParameter(ChangeClassName::to.name) as Type?
            val toNameParameter = annotation.getParameter(ChangeClassName::toName.name) as String?
            if (toParameter != null && toNameParameter != null) {
                error("both parameters [${ChangeClassName::to.name}] and [${ChangeClassName::toName.name}] were set")
            }

            val toInternalName = when {
                toParameter != null -> toParameter.internalName
                toNameParameter != null -> toNameParameter.internalName
                else -> error("both parameters [${ChangeClassName::to.name}] and [${ChangeClassName::toName.name}] weren't set")
            }

            val deleteAfterChanging =
                annotation.getParameter(ChangeClassName::deleteAfterChanging.name) as Boolean? ?: false

            val oldInternalName = processingClass.node.name
            require(oldInternalName != toInternalName)

            if (deleteAfterChanging) {
                deletedClasses.add(processingClass)
                processingClass.delete()
            } else {
                processingClass.node.invisibleAnnotations =
                    processingClass.node.invisibleAnnotations.filter { it != annotation }
                processingClass.node.name = toInternalName
                processingClass.markModified()
            }

            oldInternalName to toInternalName
        }.toMap()
        context.merge(ContextKey, values)

        fun String?.modify(onChange: (newValue: String) -> Unit) {
            var value = this ?: return
            context[ContextKey].forEach { (internalName, replacedClassInternalName) ->
                value = value.replace(internalName, replacedClassInternalName)
            }
            if (this != value) {
                onChange(value)
            }
        }

        fun MutableList<Any>?.modifyFrames(onModified: () -> Unit) {
            if (this == null) return
            var localModified = false
            forEachIndexed { index, value ->
                if (value is String) {
                    value.modify {
                        this[index] = it
                        localModified = true
                    }
                }
            }
            if (localModified) {
                onModified()
            }
        }

        fun Handle.modify(onModified: (Handle) -> Unit) {
            var localModified = false
            var owner = owner
            owner.modify {
                owner = it
                localModified = true
            }
            var desc = desc
            desc.modify {
                desc = it
                localModified = true
            }
            if (localModified) {
                onModified(Handle(
                    tag,
                    owner,
                    name,
                    desc,
                    isInterface
                ))
            }
        }

        directory.classes.filter { it !in deletedClasses }.forEach { processingClass ->
            processingClass.node.name.modify {
                processingClass.markModified()
                processingClass.node.name = it
            }
            processingClass.node.superName?.modify {
                processingClass.markModified()
                processingClass.node.superName = it
            }
            processingClass.node.interfaces?.forEachIndexed { index, superInterface ->
                superInterface.modify {
                    processingClass.markModified()
                    processingClass.node.interfaces[index] = it
                }
            }
            processingClass.node.signature.modify {
                processingClass.markModified()
                processingClass.node.signature = it
            }

            processingClass.node.fields?.forEach { field ->
                field.desc.modify {
                    processingClass.markModified()
                    field.desc = it
                }
                field.signature.modify {
                    processingClass.markModified()
                    field.signature = it
                }
            }

            processingClass.node.methods?.forEach { method ->
                method.desc.modify {
                    processingClass.markModified()
                    method.desc = it
                }
                method.signature.modify {
                    processingClass.markModified()
                    method.signature = it
                }
                method.localVariables.orEmpty().forEach { variable ->
                    variable.desc.modify {
                        processingClass.markModified()
                        variable.desc = it
                    }
                    variable.signature.modify {
                        processingClass.markModified()
                        variable.signature = it
                    }
                }
                method.instructions?.forEach { instruction ->
                    when (instruction) {
                        is MethodInsnNode -> {
                            instruction.owner.modify {
                                processingClass.markModified()
                                instruction.owner = it
                            }
                            instruction.desc.modify {
                                processingClass.markModified()
                                instruction.desc = it
                            }
                        }
                        is FieldInsnNode -> {
                            instruction.owner.modify {
                                processingClass.markModified()
                                instruction.owner = it
                            }
                            instruction.desc.modify {
                                processingClass.markModified()
                                instruction.desc = it
                            }
                        }
                        is FrameNode -> {
                            instruction.local.modifyFrames { processingClass.markModified() }
                            instruction.stack.modifyFrames { processingClass.markModified() }
                        }
                        is LdcInsnNode -> {
                            val cst = instruction.cst
                            if (cst is Type) {
                                cst.descriptor.modify {
                                    processingClass.markModified()
                                    instruction.cst = Type.getType(it)
                                }
                            }
                        }
                        is MultiANewArrayInsnNode -> {
                            instruction.desc.modify {
                                processingClass.markModified()
                                instruction.desc = it
                            }
                        }
                        is TypeInsnNode -> {
                            instruction.desc.modify {
                                processingClass.markModified()
                                instruction.desc = it
                            }
                        }
                        is InvokeDynamicInsnNode -> {
                            instruction.desc.modify {
                                processingClass.markModified()
                                instruction.desc = it
                            }
                            instruction.bsm?.modify {
                                processingClass.markModified()
                                instruction.bsm = it
                            }
                            instruction.bsmArgs?.forEachIndexed { index, arg ->
                                if (arg is Type) {
                                    arg.descriptor.modify {
                                        processingClass.markModified()
                                        instruction.bsmArgs[index] = Type.getType(it)
                                    }
                                } else if (arg is Handle) {
                                    arg.modify {
                                        processingClass.markModified()
                                        instruction.bsmArgs[index] = it
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
