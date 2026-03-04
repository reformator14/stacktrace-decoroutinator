@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContext
import dev.reformator.bytecodeprocessor.api.ProcessingDirectory
import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.eq
import dev.reformator.bytecodeprocessor.plugins.internal.readAsm
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

object GetCurrentFileNameProcessor: Processor {
    override fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext) {
        directory.classes.forEach { clazz ->
            clazz.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode) {
                        if (instruction eq currentFileNameInstruction) {
                            val fileName = clazz.node.sourceFile
                            val newInstruction = if (fileName == null) {
                                InsnNode(Opcodes.ACONST_NULL)
                            } else {
                                LdcInsnNode(fileName)
                            }
                            method.instructions.set(instruction, newInstruction)
                            clazz.markModified()
                        } else if (instruction eq currentLineNumberInstruction) {
                            var prevInstruction = instruction.previous
                            while (prevInstruction != null && prevInstruction !is LineNumberNode) {
                                prevInstruction = prevInstruction.previous
                            }
                            val lineNumber = if (prevInstruction is LineNumberNode) {
                                prevInstruction.line
                            } else {
                                -1
                            }
                            method.instructions.set(instruction, LdcInsnNode(lineNumber))
                            clazz.markModified()
                        }
                    }
                }
            }
        }
    }

    private fun currentFileNameUsage() {
        currentFileName
    }

    private fun currentLineNumberUsage() {
        currentLineNumber
    }

    private val currentFileNameInstruction: MethodInsnNode

    private val currentLineNumberInstruction: MethodInsnNode

    init {
        val methods: List<MethodNode> = GetCurrentFileNameProcessor::class.java.readAsm().methods
        currentFileNameInstruction = methods.first { it.name == ::currentFileNameUsage.name }.instructions
            .firstNotNullOf { it as? MethodInsnNode }
        currentLineNumberInstruction = methods.first { it.name == ::currentLineNumberUsage.name }.instructions
            .firstNotNullOf { it as? MethodInsnNode }
    }
}
