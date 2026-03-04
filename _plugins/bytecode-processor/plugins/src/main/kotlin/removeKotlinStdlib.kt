@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContext
import dev.reformator.bytecodeprocessor.api.ProcessingDirectory
import dev.reformator.bytecodeprocessor.api.Processor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.Objects
import kotlin.jvm.internal.Intrinsics
import kotlin.reflect.KFunction

object RemoveKotlinStdlibProcessor: Processor {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    override fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext) {
        directory.classes.forEach { clazz ->
            clazz.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode) {
                        if (
                            instruction.opcode == Opcodes.INVOKESTATIC
                            && instruction.owner == Type.getInternalName(Intrinsics::class.java)
                        ) {
                            if (
                                instruction.name in intrinsicCheckNotNullWithMessageMethodNames
                                && instruction.desc == "(${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(String::class.java)})V"
                            ) {
                                method.instructions.insert(instruction, InsnNode(Opcodes.POP2))
                                method.instructions.remove(instruction)
                                clazz.markModified()
                            } else if (
                                instruction.name in intrinsicThrowWithMessageMethodNames
                                && instruction.desc == "(${Type.getDescriptor(String::class.java)})V"
                            ) {
                                method.instructions.insert(instruction, InsnNode(Opcodes.POP))
                                method.instructions.remove(instruction)
                                clazz.markModified()
                            } else if (
                                instruction.name == run { val x: (Any) -> Unit = Intrinsics::checkNotNull; x as KFunction<*> }.name
                                && instruction.desc == "(${Type.getDescriptor(Object::class.java)})${Type.VOID_TYPE.descriptor}"
                            ) {
                                method.instructions.insert(instruction, InsnNode(Opcodes.POP))
                                method.instructions.remove(instruction)
                                clazz.markModified()
                            } else if (
                                instruction.name == run { val x: (Any?, Any?) -> Boolean = Intrinsics::areEqual; x as KFunction<*> }.name
                                && instruction.desc == "(${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(Object::class.java)})${Type.BOOLEAN_TYPE.descriptor}"
                            ) {
                                instruction.owner = Type.getInternalName(Objects::class.java)
                                instruction.name = Objects::equals.name
                            }
                        }
                    } else if (instruction is FieldInsnNode) {
                        if (
                            instruction.opcode == Opcodes.GETSTATIC
                            && instruction.owner == "kotlin/_Assertions"
                            && instruction.name == "ENABLED"
                            && instruction.desc == Type.BOOLEAN_TYPE.descriptor
                        ) {
                            method.instructions.insert(instruction, InsnNode(Opcodes.ICONST_1))
                            method.instructions.remove(instruction)
                            clazz.markModified()
                        }
                    }
                }
            }
        }
        directory.module?.let { module ->
            if (module.node.requires?.removeIf { it.module == KOTLIN_STDLIB_MODULE } == true) {
                module.markModified()
            }
        }
    }
}

private const val KOTLIN_STDLIB_MODULE = "kotlin.stdlib"

private val intrinsicCheckNotNullWithMessageMethodNames = setOf(
    run {val x: (Any?, String) -> Unit = Intrinsics::checkNotNull; x as KFunction<*> }.name,
    Intrinsics::checkExpressionValueIsNotNull.name,
    Intrinsics::checkNotNullExpressionValue.name,
    run {val x: (Any?, String) -> Unit = Intrinsics::checkReturnedValueIsNotNull; x as KFunction<*> }.name,
    run {val x: (Any?, String) -> Unit = Intrinsics::checkFieldIsNotNull; x as KFunction<*> }.name,
    Intrinsics::checkParameterIsNotNull.name,
    Intrinsics::checkNotNullParameter.name
)

private val intrinsicThrowWithMessageMethodNames = setOf(
    run {val x: (String) -> Unit = Intrinsics::throwNpe; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::throwJavaNpe; x as KFunction<*>}.name,
    Intrinsics::throwUninitializedProperty.name,
    Intrinsics::throwUninitializedPropertyAccessException.name,
    run {val x: (String) -> Unit = Intrinsics::throwAssert; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::throwIllegalArgument; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::throwIllegalState; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::throwUndefinedForReified; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::needClassReification; x as KFunction<*>}.name,
)
