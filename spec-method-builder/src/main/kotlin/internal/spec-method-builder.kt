@file:Suppress("PackageDirectoryMismatch", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal

import dev.reformator.stacktracedecoroutinator.provider.ChecksumFailedMarker
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.checksumFailedMarkerMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.nextSpecHandleMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.nextSpecMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.resumeChecksumMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.resumeCookieMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.resumeMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.specLineNumberMethodName
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.lang.invoke.MethodHandle

@Suppress("NewApi")
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
        desc = "(${Type.getType(DecoroutinatorSpec::class.java).descriptor}${Type.getType(Object::class.java).descriptor}${Type.INT_TYPE.descriptor}${Type.INT_TYPE.descriptor})${Type.getType(Object::class.java).descriptor}"
    }
    val sortedLineNumbers = lineNumbers.sorted()
    result.instructions.apply {
        val continueLabel = LabelNode()
        add(getGotoIfDepthValidInstructions(continueLabel))

        add(continueLabel)
        // First explicit frame in the method - relative to the implicit initial frame built from the
        // method's own 4 parameters (spec/result/resumeChecksum/depthChecksum), nothing has changed about
        // locals or stack by this point (IINC only changes DEPTH_CHECKSUM_VAR_INDEX's *value*, not its
        // declared type/presence), so this is F_SAME.
        add(sameFrameNode())

        add(getStoreVariablesInstructions())

        val invokeFunctionLabel = LabelNode()
        add(getGotoIfNextSpecMissingInstructions(invokeFunctionLabel))

        add(getInvokeNextSpecMethodInstructions(sortedLineNumbers))

        add(invokeFunctionLabel)
        // Frame deltas are relative to the nearest preceding explicit frame in the instruction
        // stream, not to the method's own locals - which explicit frame that is depends on whether
        // getInvokeNextSpecMethodInstructions emitted a switch at all. Non-empty lineNumbers: its
        // addByLineNumbers already emitted F_FULL case frames and a trailing same1FrameNode(Object) at
        // its own endLabel, and everything from there to invokeFunctionLabel (ASTORE, the unconditional
        // checksum/depth resets) only ever touches the *values* already held in existing local slots,
        // never adding a new slot or leaving anything on the stack - so
        // this is F_SAME relative to that endLabel frame. Empty lineNumbers: addByLineNumbers took the
        // no-switch branch and emitted no frame at all, so the nearest preceding explicit frame is
        // `continueLabel` above (4 locals: spec/result/resumeChecksum/depthChecksum) -
        // RESUME_COOKIE_VAR_INDEX/NEXT_SPEC_HANDLE_VAR_INDEX/NEXT_SPEC_VAR_INDEX are three genuinely new
        // locals relative to THAT (populated unconditionally by getStoreVariablesInstructions before any
        // branch), hence F_APPEND with those three.
        add(
            if (lineNumbers.isNotEmpty()) {
                sameFrameNode()
            } else {
                appendFrameNode(
                    Type.getType(Object::class.java).internalName,
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
private const val RESUME_CHECKSUM_VAR_INDEX = 2
private const val DEPTH_CHECKSUM_VAR_INDEX = 3
private const val RESUME_COOKIE_VAR_INDEX = 4
private const val NEXT_SPEC_HANDLE_VAR_INDEX = 5
private const val NEXT_SPEC_VAR_INDEX = 6

// The full, constant local variable layout of every generated spec method - never changes shape or
// element types across the method body (only RESULT_VAR_INDEX's/RESUME_CHECKSUM_VAR_INDEX's/
// DEPTH_CHECKSUM_VAR_INDEX's *values* are ever reassigned, reusing the method's own parameter slots - see
// getGotoIfDepthValidInstructions/getInvokeNextSpecMethodInstructions below). Used by fullLocalsFrameNode
// to hand-author F_FULL frames wherever a jump target's incoming state can't be expressed as a simple
// delta (see addByLineNumbers below); same1FrameNode/sameFrameNode/appendFrameNode cover the handful of
// spots where a delta relative to the nearest preceding explicit frame is simpler and correct -
// ClassWriter here only runs with COMPUTE_MAXS (not COMPUTE_FRAMES, see generator-jvm's
// classLoader-generator.kt and class-transformer.kt), so every frame below is taken verbatim; there is
// no ASM-computed fallback to catch a wrong one except a VerifyError at class-load time.
@Suppress("NewApi")
private val fullLocals = mapOf(
    SPEC_VAR_INDEX to Type.getType(DecoroutinatorSpec::class.java).internalName,
    RESULT_VAR_INDEX to Type.getType(Object::class.java).internalName,
    RESUME_CHECKSUM_VAR_INDEX to Opcodes.INTEGER,
    DEPTH_CHECKSUM_VAR_INDEX to Opcodes.INTEGER,
    RESUME_COOKIE_VAR_INDEX to Type.getType(Object::class.java).internalName,
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

// Depth guard: bounds the recursive descent through the spec chain so a structurally-cyclic chain (e.g.
// from a shared spec holder racily reused by two unrelated logical resumptions - see CLAUDE.md's
// resumeChecksum entry) fails cleanly instead of recursing until the real JVM stack overflows.
// depthChecksum is seeded to the exact chain length when the chain is built (common/awakener.kt's
// buildSpecChain), decremented by exactly 1 on every dispatch into a spec method; a well-formed,
// non-cyclic chain always reaches exactly DEPTH_VALID (0) at the point it actually resumes anything - see
// utils-provider.kt's resume(), which checks depthChecksum == DEPTH_VALID unconditionally, the same way
// it already checks resumeChecksum. IINC decrements the parameter slot in place (no separate "updated"
// local - generated bytecode has no restriction against reassigning a parameter slot, unlike unknown()'s
// Kotlin source). DEPTH_VALID is inlined as the literal comparand 0 via IFGE, never loaded via reflection
// or an extra provider call.
private fun getGotoIfDepthValidInstructions(
    label: LabelNode
) = InsnList().apply {
    add(IincInsnNode(DEPTH_CHECKSUM_VAR_INDEX, -1))
    add(VarInsnNode(Opcodes.ILOAD, DEPTH_CHECKSUM_VAR_INDEX))
    add(JumpInsnNode(Opcodes.IFGE, label))
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        Type.getType(DecoroutinatorSpec::class.java).internalName,
        checksumFailedMarkerMethodName,
        "()${Type.getType(ChecksumFailedMarker::class.java).descriptor}",
    ))
    add(InsnNode(Opcodes.ARETURN))
}

// spec.$decoroutinator$resumeCookie/$decoroutinator$getResumeChecksum()/$decoroutinator$nextSpecHandle/
// $decoroutinator$nextSpec are plain (no longer one-shot) getters - a spec object can now be shared/
// reused across concurrent resumptions (see provider-api.kt's SharedSpec and CLAUDE.md's resumeChecksum
// entry), so each is read exactly once here into its own local and reused from there on, guaranteeing
// every use within this single spec method invocation sees the same snapshot even if another thread
// concurrently re-`$decoroutinator$init`s the same shared spec.
@Suppress("NewApi")
private fun getStoreVariablesInstructions() = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        Type.getType(DecoroutinatorSpec::class.java).internalName,
        resumeCookieMethodName,
        "()${Type.getType(Object::class.java).descriptor}",
    ))
    add(VarInsnNode(Opcodes.ASTORE, RESUME_COOKIE_VAR_INDEX))

    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(VarInsnNode(Opcodes.ILOAD, RESUME_CHECKSUM_VAR_INDEX))
    add(VarInsnNode(Opcodes.ALOAD, RESUME_COOKIE_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        Type.getType(DecoroutinatorSpec::class.java).internalName,
        resumeChecksumMethodName,
        "(${Type.INT_TYPE.descriptor}${Type.getType(Object::class.java).descriptor})${Type.INT_TYPE.descriptor}",
    ))
    add(VarInsnNode(Opcodes.ISTORE, RESUME_CHECKSUM_VAR_INDEX))

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
        "()${Type.getType(DecoroutinatorSpec::class.java).descriptor}",
    ))
    add(VarInsnNode(Opcodes.ASTORE, NEXT_SPEC_VAR_INDEX))
}

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

// Mirrors mh-invoker's unknown() fallback exactly: invoke the next spec method passing the current
// resumeChecksum/depthChecksum along, store the (possibly ChecksumFailedMarker) result back into
// RESULT_VAR_INDEX - Kotlin's `updatedResult`/`updatedResumeChecksum`/`updatedDepthChecksum` locals only
// exist because Kotlin won't let `unknown()` reassign its own value parameters; generated bytecode has no
// such restriction, so this reuses the method's own result/resumeChecksum/depthChecksum parameter slots
// directly - then unconditionally resets both resumeChecksum and depthChecksum back to their valid
// sentinels (both inlined as literal 0 here, never loaded via reflection or an extra provider call). The
// reset is unconditional - not gated on whether the nested call actually succeeded - because the shared
// `resume()` helper (utils-provider.kt) already short-circuits on `result.isChecksumFailedMarker` before
// it ever looks at resumeChecksum/depthChecksum, so a failed nested call still propagates correctly as
// ChecksumFailedMarker regardless of what these two get reset to; checking first (as this used to, via
// the now-removed `$decoroutinator$isChecksumFailedMarker`) was redundant. Execution then falls straight
// through into `invokeFunctionLabel`, the same merge point `getGotoIfNextSpecMissingInstructions` jumps to
// when there's no next spec at all.
@Suppress("NewApi")
private fun getInvokeNextSpecMethodInstructions(
    lineNumbers: List<Int>
) = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, NEXT_SPEC_HANDLE_VAR_INDEX))
    add(VarInsnNode(Opcodes.ALOAD, NEXT_SPEC_VAR_INDEX))
    add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
    add(VarInsnNode(Opcodes.ILOAD, RESUME_CHECKSUM_VAR_INDEX))
    add(VarInsnNode(Opcodes.ILOAD, DEPTH_CHECKSUM_VAR_INDEX))
    addByLineNumbers(
        lineNumbers = lineNumbers,
        addBeforeFrame = { fullLocalsFrameNode(
            Type.getType(MethodHandle::class.java).internalName,
            Type.getType(DecoroutinatorSpec::class.java).internalName,
            Type.getType(Object::class.java).internalName,
            Opcodes.INTEGER,
            Opcodes.INTEGER
        ) },
        addAfterFrame = { same1FrameNode(Type.getType(Object::class.java).internalName) }
    ) {
        add(MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            Type.getType(MethodHandle::class.java).internalName,
            MethodHandle::invokeExact.name,
            "(${Type.getType(DecoroutinatorSpec::class.java).descriptor}${Type.getType(Object::class.java).descriptor}${Type.INT_TYPE.descriptor}${Type.INT_TYPE.descriptor})${Type.getType(Object::class.java).descriptor}"
        ))
    }
    add(VarInsnNode(Opcodes.ASTORE, RESULT_VAR_INDEX))

    add(InsnNode(Opcodes.ICONST_0))
    add(VarInsnNode(Opcodes.ISTORE, RESUME_CHECKSUM_VAR_INDEX))
    add(InsnNode(Opcodes.ICONST_0))
    add(VarInsnNode(Opcodes.ISTORE, DEPTH_CHECKSUM_VAR_INDEX))
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

private fun getResumeNextAndReturnInstructions(lineNumbers: List<Int>) = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
    add(VarInsnNode(Opcodes.ILOAD, RESUME_CHECKSUM_VAR_INDEX))
    add(VarInsnNode(Opcodes.ILOAD, DEPTH_CHECKSUM_VAR_INDEX))
    add(VarInsnNode(Opcodes.ALOAD, RESUME_COOKIE_VAR_INDEX))
    addByLineNumbers(
        lineNumbers = lineNumbers,
        addBeforeFrame = { fullLocalsFrameNode(
            Type.getType(DecoroutinatorSpec::class.java).internalName,
            Type.getType(Object::class.java).internalName,
            Opcodes.INTEGER,
            Opcodes.INTEGER,
            Type.getType(Object::class.java).internalName
        ) },
        addAfterFrame = { same1FrameNode(Type.getType(Object::class.java).internalName) }
    ) {
        add(MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            Type.getType(DecoroutinatorSpec::class.java).internalName,
            resumeMethodName,
            "(${Type.getType(Object::class.java).descriptor}${Type.INT_TYPE.descriptor}${Type.INT_TYPE.descriptor}${Type.getType(Object::class.java).descriptor})${Type.getType(Object::class.java).descriptor}"
        ))
    }
    add(InsnNode(Opcodes.ARETURN))
}
