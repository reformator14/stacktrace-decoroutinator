@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.nextSpecHandleMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.nextSpecMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.resumeNextMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.specLineNumberMethodName
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.lang.invoke.MethodHandle

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "NewApi")
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
        add(getStoreVariablesInstructions())

        val invokeFunctionLabel = LabelNode()
        add(getGotoIfNextSpecMissingInstructions(invokeFunctionLabel))

        add(getInvokeNextSpecMethodInstructions(sortedLineNumbers))

        add(invokeFunctionLabel)
        // Frame deltas are relative to the nearest preceding explicit frame in the instruction
        // stream, not to the method's own locals - which explicit frame that is depends on whether
        // getInvokeNextSpecMethodInstructions emitted a switch at all. Non-empty lineNumbers: its
        // addByLineNumbers already emitted F_FULL case frames and a trailing same1FrameNode(Object) at
        // its own endLabel, so here (stack now empty again, after the ASTORE) is F_SAME relative to
        // that. Empty lineNumbers: addByLineNumbers took the no-switch branch and emitted no frame at
        // all, so this is the FIRST explicit frame in the method - relative to the method's own
        // initial locals (just its two parameters, spec/result), NEXT_SPEC_HANDLE_VAR_INDEX/
        // NEXT_SPEC_VAR_INDEX are two genuinely new locals, hence F_APPEND.
        add(
            if (lineNumbers.isNotEmpty()) {
                sameFrameNode()
            } else {
                appendFrameNode(
                    Type.getType(MethodHandle::class.java).internalName,
                    Type.getType(DecoroutinatorSpec::class.java).internalName
                )
            }
        )
        add(getResumeNextAndReturnInstructions(sortedLineNumbers))
    }
    return result
}

private const val SPEC_VAR_INDEX = 0
private const val RESULT_VAR_INDEX = 1
private const val NEXT_SPEC_HANDLE_VAR_INDEX = 2
private const val NEXT_SPEC_VAR_INDEX = 3

// The full, constant local variable layout of every generated spec method - never changes shape or
// element types across the method body (only RESULT_VAR_INDEX's *value* is ever reassigned). Used by
// fullLocalsFrameNode to hand-author F_FULL frames wherever a jump target's incoming state can't be
// expressed as a simple delta (see addByLineNumbers below); same1FrameNode/sameFrameNode/appendFrameNode
// cover the handful of spots where a delta relative to the nearest preceding explicit frame is simpler
// and correct - ClassWriter here only runs with COMPUTE_MAXS (not COMPUTE_FRAMES, see generator-jvm's
// classLoader-generator.kt and class-transformer.kt), so every frame below is taken verbatim; there is
// no ASM-computed fallback to catch a wrong one except a VerifyError at class-load time.
@Suppress("NewApi", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private val fullLocals = mapOf(
    SPEC_VAR_INDEX to Type.getType(DecoroutinatorSpec::class.java).internalName,
    RESULT_VAR_INDEX to Type.getType(Object::class.java).internalName,
    NEXT_SPEC_HANDLE_VAR_INDEX to Type.getType(MethodHandle::class.java).internalName,
    NEXT_SPEC_VAR_INDEX to Type.getType(DecoroutinatorSpec::class.java).internalName
).let { map -> Array<Any>(map.size) { map[it]!! } }

private fun fullLocalsFrameNode(vararg stack: Any) =
    FrameNode(Opcodes.F_FULL, fullLocals.size, fullLocals, stack.size, stack)

private fun same1FrameNode(stackItem: Any) =
    FrameNode(Opcodes.F_SAME1, 0, null, 1, arrayOf(stackItem))

private fun sameFrameNode() =
    FrameNode(Opcodes.F_SAME, 0, null, 0, null)

private fun appendFrameNode(vararg locals: Any) =
    FrameNode(Opcodes.F_APPEND, locals.size, locals, 0, null)

@Suppress("NewApi")
private fun getStoreVariablesInstructions() = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        Type.getType(DecoroutinatorSpec::class.java).internalName,
        nextSpecHandleMethodName,
        "()${Type.getType(MethodHandle::class.java).descriptor}",
    ))
    add(VarInsnNode(Opcodes.ASTORE, NEXT_SPEC_HANDLE_VAR_INDEX))

    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        Type.getType(DecoroutinatorSpec::class.java).internalName,
        nextSpecMethodName,
        "()${Type.getType(DecoroutinatorSpec::class.java).descriptor}"
    ))
    add(VarInsnNode(Opcodes.ASTORE, NEXT_SPEC_VAR_INDEX))
}

// spec.$decoroutinator$getNextSpecHandle()/$decoroutinator$getNextSpec() are one-shot getters (they null
// out their backing field as they're read), so they're read exactly once via getStoreVariablesInstructions
// above and reused from NEXT_SPEC_HANDLE_VAR_INDEX/NEXT_SPEC_VAR_INDEX from here on - never re-invoked.
// Both must be non-null to call the next spec method; either being null means this is the last spec in
// the chain, so skip straight to resuming this spec's own continuation.
private fun getGotoIfNextSpecMissingInstructions(
    label: LabelNode
) = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, NEXT_SPEC_HANDLE_VAR_INDEX))
    add(JumpInsnNode(Opcodes.IFNULL, label))
    add(VarInsnNode(Opcodes.ALOAD, NEXT_SPEC_VAR_INDEX))
    add(JumpInsnNode(Opcodes.IFNULL, label))
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "NewApi")
private fun getInvokeNextSpecMethodInstructions(lineNumbers: List<Int>) = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, NEXT_SPEC_HANDLE_VAR_INDEX))
    add(VarInsnNode(Opcodes.ALOAD, NEXT_SPEC_VAR_INDEX))
    add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
    addByLineNumbers(
        lineNumbers = lineNumbers,
        addBeforeFrame = { fullLocalsFrameNode(
            Type.getType(MethodHandle::class.java).internalName,
            Type.getType(DecoroutinatorSpec::class.java).internalName,
            Type.getType(Object::class.java).internalName
        ) },
        addAfterFrame = { same1FrameNode(Type.getType(Object::class.java).internalName) }
    ) {
        add(MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            Type.getType(MethodHandle::class.java).internalName,
            MethodHandle::invokeExact.name,
            "(${Type.getType(DecoroutinatorSpec::class.java).descriptor}${Type.getType(Object::class.java).descriptor})${Type.getType(Object::class.java).descriptor}"
        ))
    }
    add(VarInsnNode(Opcodes.ASTORE, RESULT_VAR_INDEX))
}

// Builds a LookupSwitch over `lineNumbers`, one case per known line (each carrying its own
// LineNumberNode so a stack trace captured while executing that case reports the original source
// line), plus an always-present default case running the exact same `action` with no LineNumberNode -
// reached whenever the runtime line doesn't match any known case, rather than throwing. When
// `lineNumbers` is empty there's nothing to dispatch on, so `action` just runs directly - no
// `$decoroutinator$getLineNumber()` call, no switch at all. Otherwise the default case is placed first
// (right after the switch instruction, mirroring generator-android's equivalent dex switch, where a miss
// falls through to whatever physically follows it) and always GOTOs past the labeled cases; only the
// physically-last labeled case skips its own GOTO, since it already sits right next to `endLabel`.
// `addBeforeFrame`/`addAfterFrame` let each caller supply the F_FULL frame for every case/default entry
// point and the (delta) frame for the shared exit, since the two callers below push a different stack
// shape onto the operand stack before dispatching.
private fun InsnList.addByLineNumbers(
    lineNumbers: List<Int>,
    addBeforeFrame: () -> FrameNode,
    addAfterFrame: () -> FrameNode,
    action: InsnList.() -> Unit
) {
    if (lineNumbers.isEmpty()) {
        action()
    } else {
        val labels = Array(lineNumbers.size) { LabelNode() }
        val defaultLabel = LabelNode()
        val endLabel = LabelNode()

        add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
        add(MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            Type.getType(DecoroutinatorSpec::class.java).internalName,
            specLineNumberMethodName,
            "()${Type.INT_TYPE.descriptor}",
        ))
        add(LookupSwitchInsnNode(defaultLabel, lineNumbers.toIntArray(), labels))

        add(defaultLabel)
        add(addBeforeFrame())
        action()
        add(JumpInsnNode(Opcodes.GOTO, endLabel))

        labels.forEachIndexed { index, label ->
            add(label)
            add(addBeforeFrame())
            add(LineNumberNode(lineNumbers[index], label))
            action()
            if (index < labels.lastIndex) {
                add(JumpInsnNode(Opcodes.GOTO, endLabel))
            }
        }

        add(endLabel)
        add(addAfterFrame())
    }
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private fun getResumeNextAndReturnInstructions(lineNumbers: List<Int>) = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
    addByLineNumbers(
        lineNumbers = lineNumbers,
        addBeforeFrame = { fullLocalsFrameNode(
            Type.getType(DecoroutinatorSpec::class.java).internalName,
            Type.getType(Object::class.java).internalName
        ) },
        addAfterFrame = { same1FrameNode(Type.getType(Object::class.java).internalName) }
    ) {
        add(MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            Type.getType(DecoroutinatorSpec::class.java).internalName,
            resumeNextMethodName,
            "(${Type.getType(Object::class.java).descriptor})${Type.getType(Object::class.java).descriptor}"
        ))
    }
    add(InsnNode(Opcodes.ARETURN))
}
