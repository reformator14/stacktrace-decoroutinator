@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.tests.bytecodeprocessor

import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContext
import dev.reformator.bytecodeprocessor.api.ProcessingDirectory
import dev.reformator.bytecodeprocessor.api.Processor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

@Suppress("unused")
class AddOpcodeTraceProcessor: Processor {
    override fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext) {
        directory.classes.forEach { processingClass ->
            processingClass.node.methods?.forEach method@{ method ->
                val traceOpcodeSuffix = "\$TraceOpcode"

                val annotation = method.invisibleAnnotations.orEmpty().find { annotation ->
                    Type.getType(annotation.desc).internalName.endsWith(traceOpcodeSuffix)
                } ?: return@method

                val traceOpcodeClassInternalName =
                    Type.getType(annotation.desc).internalName.removeSuffix(traceOpcodeSuffix)

                val className = Type.getObjectType(processingClass.node.name).className

                method.invisibleAnnotations = method.invisibleAnnotations.filter { it != annotation }
                method.instructions.forEach instruction@{ instruction ->
                    if (instruction.opcode == -1) return@instruction

                    method.instructions.insertBefore(instruction, InsnList().apply {
                        add(LdcInsnNode(className))
                        add(LdcInsnNode(method.name))
                        add(LdcInsnNode(instruction.opcode))
                        add(MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            traceOpcodeClassInternalName,
                            "traceOpcode",
                            "(${Type.getType(String::class.java).descriptor}" +
                                "${Type.getType(String::class.java).descriptor}" +
                                "${Type.INT_TYPE.descriptor}" +
                                ")${Type.VOID_TYPE.descriptor}"
                        ))
                    })
                }

                processingClass.markModified()
            }
        }
    }
}