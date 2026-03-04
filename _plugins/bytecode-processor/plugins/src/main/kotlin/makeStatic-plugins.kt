@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.MakeStatic
import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContext
import dev.reformator.bytecodeprocessor.api.ProcessingDirectory
import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.getOrCreateClinit
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import dev.reformator.bytecodeprocessor.plugins.internal.isStatic
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode

object MakeStaticProcessor: Processor {
    data class Key(
        val classInternalName: String,
        val methodName: String,
        val methodDesc: String
    )

    object ContextKey: BytecodeProcessorContext.Key<Set<Key>> {
        override val id: String
            get() = "makeStaticMethods"
        override val default: Set<Key>
            get() = emptySet()
        override fun merge(value1: Set<Key>, value2: Set<Key>): Set<Key> =
            value1 + value2
    }

    override fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext) {
        val values = directory.classes.flatMap { processingClass ->
            val keysToAddToStaticInitializer: MutableSet<Key> = hashSetOf()
            val list = processingClass.node.methods.orEmpty().mapNotNull { method ->
                val annotation = method.invisibleAnnotations.find(MakeStatic::class.java)
                    ?: return@mapNotNull null

                val methodType = Type.getMethodType(method.desc)

                require(!method.isStatic)
                val key = Key(
                    classInternalName = processingClass.node.name,
                    methodName = method.name,
                    methodDesc = method.desc
                )

                method.access = method.access or Opcodes.ACC_STATIC
                method.desc = key.newMethodDesc
                if (annotation.getParameter(MakeStatic::addToStaticInitializer.name) as Boolean? ?: false) {
                    require(methodType.argumentCount == 0)
                    require(methodType.returnType == Type.VOID_TYPE)
                    keysToAddToStaticInitializer.add(key)
                }

                method.invisibleAnnotations = method.invisibleAnnotations.filter { it != annotation }
                processingClass.markModified()

                key
            }
            if (keysToAddToStaticInitializer.isNotEmpty()) {
                val clinit = processingClass.node.getOrCreateClinit()
                val firstInstruction = clinit.instructions.first
                keysToAddToStaticInitializer.forEach { key ->
                    require(key.classInternalName == processingClass.node.name)
                    clinit.instructions.insertBefore(
                        firstInstruction,
                        InsnList().apply {
                            add(InsnNode(Opcodes.ACONST_NULL))
                            add(MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                key.classInternalName,
                                key.methodName,
                                key.newMethodDesc
                            ))
                        }
                    )
                }
            }
            list
        }.toSet()
        context.merge(ContextKey, values)

        directory.classes.forEach { processingClass ->
            processingClass.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode && instruction.opcode != Opcodes.INVOKESTATIC) {
                        val key = Key(
                            classInternalName = instruction.owner,
                            methodName = instruction.name,
                            methodDesc = instruction.desc
                        )
                        if (key in context[ContextKey]) {
                            instruction.opcode = Opcodes.INVOKESTATIC
                            instruction.desc = key.newMethodDesc
                            processingClass.markModified()
                        }
                    }
                }
            }
        }
    }
}

private val MakeStaticProcessor.Key.newMethodDesc: String
    get() {
        val methodType = Type.getMethodType(methodDesc)
        val newMethodType = Type.getMethodType(
            methodType.returnType,
            *Array<Type>(methodType.argumentTypes.size + 1) {
                when (it) {
                    0 -> Type.getObjectType(classInternalName)
                    else -> methodType.argumentTypes[it - 1]
                }
            }
        )
        return newMethodType.descriptor
    }
