@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.UNKNOWN_LINE_NUMBER
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.isLastSpecMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.nextSpecHandleMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.nextSpecMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.resumeNextMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.specLineNumberMethodName
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.lang.StringBuilder
import java.lang.invoke.MethodHandle

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
fun buildSpecMethodNode(
    methodName: String,
    lineNumbers: Set<Int>,
    makePrivate: Boolean,
    makeFinal: Boolean
): MethodNode {
    val result = MethodNode(Opcodes.ASM9).apply {
        access = if (makePrivate) Opcodes.ACC_PRIVATE else Opcodes.ACC_PUBLIC
        if (makeFinal) {
            access = access or Opcodes.ACC_FINAL
        }
        access = access or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC
        name = methodName
        desc = "(${Type.getType(DecoroutinatorSpec::class.java).descriptor}${Type.getType(Object::class.java).descriptor})${Type.getType(Object::class.java).descriptor}"
    }
    val sortedLineNumbers = lineNumbers.sorted()
    result.instructions.apply {
        add(getStoreLineNumberInstructions())

        val invokeFunctionLabel = LabelNode()
        add(getGotoIfLastSpecInstructions(invokeFunctionLabel))

        val invalidLineNumberLabel = LabelNode()
        add(getInvokeNextSpecMethodInstructions(
            invalidLineNumberLabel = invalidLineNumberLabel,
            lineNumbers = sortedLineNumbers
        ))

        add(invokeFunctionLabel)
        add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
        add(getResumeNextAndReturnInstructions(sortedLineNumbers))

        add(invalidLineNumberLabel)
        add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
        add(getThrowInvalidLineNumberInstructions())
    }
    return result
}

private const val SPEC_VAR_INDEX = 0
private const val RESULT_VAR_INDEX = 1
private const val LINE_NUMBER_VAR_INDEX = 2

private fun getStoreLineNumberInstructions() = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        Type.getType(DecoroutinatorSpec::class.java).internalName,
        specLineNumberMethodName,
        "()${Type.INT_TYPE.descriptor}"
    ))
    add(VarInsnNode(Opcodes.ISTORE, LINE_NUMBER_VAR_INDEX))
}

private fun getGotoIfLastSpecInstructions(
    label: LabelNode
) = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        Type.getType(DecoroutinatorSpec::class.java).internalName,
        isLastSpecMethodName,
        "()${Type.BOOLEAN_TYPE.descriptor}",
    ))
    add(JumpInsnNode(Opcodes.IFNE, label))
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private fun getInvokeNextSpecMethodInstructions(
    invalidLineNumberLabel: LabelNode,
    lineNumbers: List<Int>
) = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        Type.getType(DecoroutinatorSpec::class.java).internalName,
        nextSpecHandleMethodName,
        "()${Type.getType(MethodHandle::class.java).descriptor}",
    ))
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        Type.getType(DecoroutinatorSpec::class.java).internalName,
        nextSpecMethodName,
        "()${Type.getType(DecoroutinatorSpec::class.java).descriptor}"
    ))
    add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
    val invalidLabel = LabelNode()
    val endLabel = LabelNode()
    addByLineNumbers(invalidLabel, lineNumbers, false) {
        add(FrameNode(
            Opcodes.F_FULL,
            3,
            arrayOf<Any>(
                Type.getType(DecoroutinatorSpec::class.java).internalName,
                Type.getType(Object::class.java).internalName,
                Opcodes.INTEGER,
            ),
            3,
            arrayOf(
                Type.getType(MethodHandle::class.java).internalName,
                Type.getType(DecoroutinatorSpec::class.java).internalName,
                Type.getType(Object::class.java).internalName
            )
        ))
        add(MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            Type.getType(MethodHandle::class.java).internalName,
            MethodHandle::invokeExact.name,
            "(${Type.getType(DecoroutinatorSpec::class.java).descriptor}${Type.getType(Object::class.java).descriptor})${Type.getType(Object::class.java).descriptor}"
        ))
        add(JumpInsnNode(Opcodes.GOTO, endLabel))
    }
    add(invalidLabel)
    add(FrameNode(
        Opcodes.F_FULL,
        3,
        arrayOf<Any>(
            Type.getType(DecoroutinatorSpec::class.java).internalName,
            Type.getType(Object::class.java).internalName,
            Opcodes.INTEGER,
        ),
        3,
        arrayOf(
            Type.getType(MethodHandle::class.java).internalName,
            Type.getType(DecoroutinatorSpec::class.java).internalName,
            Type.getType(Object::class.java).internalName
        )
    ))
    add(InsnNode(Opcodes.POP2))
    add(InsnNode(Opcodes.POP))
    add(JumpInsnNode(Opcodes.GOTO, invalidLineNumberLabel))
    add(endLabel)
    add(FrameNode(Opcodes.F_SAME1, 0, null, 1, arrayOf(Type.getType(Object::class.java).internalName)))
    add(VarInsnNode(Opcodes.ASTORE, RESULT_VAR_INDEX))
}

private fun InsnList.addByLineNumbers(
    invalidLineNumberLabel: LabelNode,
    lineNumbers: List<Int>,
    appendGotoEnd: Boolean = true,
    action: InsnList.(lineNumber: Int) -> Unit
) {
    val array = lineNumbers.toIntArray()
    val labels = Array(lineNumbers.size) { LabelNode() }
    val endLabel = if (appendGotoEnd) LabelNode() else null
    add(VarInsnNode(Opcodes.ILOAD, LINE_NUMBER_VAR_INDEX))
    add(LookupSwitchInsnNode(invalidLineNumberLabel, array, labels))
    array.forEachIndexed { index, lineNumber ->
        val label = labels[index]
        add(label)
        if (lineNumber != UNKNOWN_LINE_NUMBER) {
            add(LineNumberNode(lineNumber, label))
        }
        action(lineNumber)
        if (endLabel != null && index < array.lastIndex) {
            add(JumpInsnNode(Opcodes.GOTO, endLabel))
        }
    }
    if (endLabel != null) {
        add(endLabel)
    }
}

private fun getThrowInvalidLineNumberInstructions() = InsnList().apply {
    add(TypeInsnNode(Opcodes.NEW, Type.getType(IllegalArgumentException::class.java).internalName))
    add(InsnNode(Opcodes.DUP))
    add(TypeInsnNode(Opcodes.NEW, Type.getType(StringBuilder::class.java).internalName))
    add(InsnNode(Opcodes.DUP))
    add(LdcInsnNode("invalid line number: "))
    add(MethodInsnNode(
        Opcodes.INVOKESPECIAL,
        Type.getType(StringBuilder::class.java).internalName,
        "<init>",
        "(${Type.getType(String::class.java).descriptor})${Type.VOID_TYPE.descriptor}"
    ))
    add(VarInsnNode(Opcodes.ILOAD, LINE_NUMBER_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEVIRTUAL,
        Type.getType(StringBuilder::class.java).internalName,
        "append",
        "(${Type.INT_TYPE.descriptor})${Type.getType(StringBuilder::class.java).descriptor}"
    ))
    add(MethodInsnNode(
        Opcodes.INVOKEVIRTUAL,
        Type.getType(StringBuilder::class.java).internalName,
        StringBuilder::toString.name,
        "()${Type.getType(String::class.java).descriptor}")
    )
    add(MethodInsnNode(
        Opcodes.INVOKESPECIAL,
        Type.getType(IllegalArgumentException::class.java).internalName,
        "<init>",
        "(${Type.getType(String::class.java).descriptor})${Type.VOID_TYPE.descriptor}")
    )
    add(InsnNode(Opcodes.ATHROW))
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private fun getResumeNextAndReturnInstructions(lineNumbers: List<Int>) = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
    val invalidLabel = LabelNode()
    addByLineNumbers(invalidLabel, lineNumbers) {
        add(FrameNode(
            Opcodes.F_FULL,
            3,
            arrayOf<Any>(
                Type.getType(DecoroutinatorSpec::class.java).internalName,
                Type.getType(Object::class.java).internalName,
                Opcodes.INTEGER,
            ),
            2,
            arrayOf(
                Type.getType(DecoroutinatorSpec::class.java).internalName,
                Type.getType(Object::class.java).internalName
            )
        ))
        add(MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            Type.getType(DecoroutinatorSpec::class.java).internalName,
            resumeNextMethodName,
            "(${Type.getType(Object::class.java).descriptor})${Type.getType(Object::class.java).descriptor}"
        ))
    }
    add(FrameNode(
        Opcodes.F_SAME1,
        0,
        null,
        1,
        arrayOf(Type.getInternalName(Object::class.java))
    ))
    add(InsnNode(Opcodes.ARETURN))
    add(invalidLabel)
    add(FrameNode(
        Opcodes.F_FULL,
        3,
        arrayOf<Any>(
            Type.getType(DecoroutinatorSpec::class.java).internalName,
            Type.getType(Object::class.java).internalName,
            Opcodes.INTEGER,
        ),
        2,
        arrayOf(
            Type.getType(DecoroutinatorSpec::class.java).internalName,
            Type.getType(Object::class.java).internalName
        )
    ))
    add(InsnNode(Opcodes.POP2))
}
