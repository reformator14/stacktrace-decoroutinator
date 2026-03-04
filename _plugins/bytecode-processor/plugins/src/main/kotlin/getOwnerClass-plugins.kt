@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.ownerClass
import dev.reformator.bytecodeprocessor.intrinsics.ownerClassName
import dev.reformator.bytecodeprocessor.intrinsics.ownerMethodName
import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContext
import dev.reformator.bytecodeprocessor.api.ProcessingDirectory
import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.eq
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import dev.reformator.bytecodeprocessor.plugins.internal.isStatic
import dev.reformator.bytecodeprocessor.plugins.internal.readAsm
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import kotlin.collections.first

object GetOwnerClassProcessor: Processor {
    object ContextKey: BytecodeProcessorContext.Key<Set<Key>> {
        override val id: String
            get() = "getOwnerClassMethods"
        override val default: Set<Key>
            get() = emptySet()
        override fun merge(value1: Set<Key>, value2: Set<Key>): Set<Key> =
            value1 + value2
    }

    data class Key(
        val internalClassName: String,
        val methodName: String,
        val isString: Boolean
    )

    override fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext) {
        val values = directory.classes.flatMap { processingClass ->
            val methodsToDelete: MutableCollection<MethodNode> = mutableListOf()
            val list = processingClass.node.methods.orEmpty().mapNotNull { method ->
                val annotation = method.invisibleAnnotations.find(GetOwnerClass::class.java)
                    ?: return@mapNotNull null

                require(method.isStatic)

                val isString = when (method.desc) {
                    classMethodDesc -> false
                    stringMethodDesc -> true
                    else -> error(
                        "invalid @GetOwnerClass desc [${method.desc}] for class [${processingClass.node.name}] "
                        + "and method [${method.name}]"
                    )
                }

                val deleteAfterChanging =
                    annotation.getParameter(GetOwnerClass::deleteAfterModification.name) as Boolean? ?: true

                if (deleteAfterChanging) {
                    methodsToDelete.add(method)
                } else {
                    method.invisibleAnnotations = method.invisibleAnnotations.filter { it != annotation }
                }
                processingClass.markModified()

                Key(
                    internalClassName = processingClass.node.name,
                    methodName = method.name,
                    isString = isString
                )
            }
            if (methodsToDelete.isNotEmpty()) {
                processingClass.node.methods = processingClass.node.methods.filter { it !in methodsToDelete }
            }
            list
        }.toSet()
        context.merge(ContextKey, values)

        directory.classes.forEach { processingClass ->
            processingClass.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode) {
                        if (instruction eq ownerMethodNameInstruction) {
                            method.instructions.set(
                                instruction,
                                LdcInsnNode(method.name)
                            )
                            processingClass.markModified()
                        } else if (instruction eq ownerClassInstruction) {
                            method.instructions.set(
                                instruction,
                                LdcInsnNode(Type.getObjectType(processingClass.node.name))
                            )
                            processingClass.markModified()
                        } else if (instruction eq ownerClassNameInstruction) {
                            method.instructions.set(
                                instruction,
                                LdcInsnNode(Type.getObjectType(processingClass.node.name).className)
                            )
                            processingClass.markModified()
                        } else if (instruction.opcode == Opcodes.INVOKESTATIC) {
                            val isString = when(instruction.desc) {
                                classMethodDesc -> false
                                stringMethodDesc -> true
                                else -> null
                            }
                            if (isString != null) {
                                val key = Key(
                                    internalClassName = instruction.owner,
                                    methodName = instruction.name,
                                    isString = isString
                                )
                                if (key in context[ContextKey]) {
                                    val type: Type = Type.getObjectType(instruction.owner)
                                    val ldcConst: Any = if (isString) type.className else type
                                    method.instructions.set(instruction, LdcInsnNode(ldcConst))
                                    processingClass.markModified()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun usageOwnerClass() {
        ownerClass
    }
    private fun usageOwnerClassName() {
        ownerClassName
    }
    private fun usageOwnerMethodName() {
        ownerMethodName
    }

    private val ownerClassInstruction: MethodInsnNode
    private val ownerClassNameInstruction: MethodInsnNode
    private val ownerMethodNameInstruction: MethodInsnNode

    init {
        val methods: List<MethodNode> = GetOwnerClassProcessor::class.java.readAsm().methods
        ownerClassInstruction = methods
            .first { it.name == GetOwnerClassProcessor::usageOwnerClass.name }
            .instructions
            .firstNotNullOf { it as? MethodInsnNode }
        ownerClassNameInstruction = methods
            .first { it.name == GetOwnerClassProcessor::usageOwnerClassName.name }
            .instructions
            .firstNotNullOf { it as? MethodInsnNode }
        ownerMethodNameInstruction = methods
            .first { it.name == GetOwnerClassProcessor::usageOwnerMethodName.name }
            .instructions
            .firstNotNullOf { it as? MethodInsnNode }
    }
}

private val classMethodDesc = "()${Type.getDescriptor(Class::class.java)}"
private val stringMethodDesc = "()${Type.getDescriptor(String::class.java)}"
