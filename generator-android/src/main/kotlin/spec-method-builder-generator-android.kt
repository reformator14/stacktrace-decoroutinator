@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("SpecMethodBuilderGeneratorAndroidKt")

package dev.reformator.stacktracedecoroutinator.generatorandroid

import com.android.dx.dex.DexOptions
import com.android.dx.dex.code.*
import com.android.dx.dex.file.EncodedMethod
import com.android.dx.rop.code.RegisterSpec
import com.android.dx.rop.code.RegisterSpecList
import com.android.dx.rop.code.SourcePosition
import com.android.dx.rop.cst.*
import com.android.dx.rop.type.StdTypeList
import com.android.dx.rop.type.Type
import com.android.dx.util.IntList
import dev.reformator.stacktracedecoroutinator.provider.ChecksumFailedMarker
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.checksumFailedMarkerMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
import dev.reformator.stacktracedecoroutinator.provider.internal.nextSpecHandleMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.nextSpecMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.resumeChecksumMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.resumeCookieMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.resumeMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.specLineNumberMethodName
import java.lang.invoke.MethodHandle
import java.lang.reflect.Modifier

internal fun buildSpecMethod(
    dexOptions: DexOptions,
    clazz: CstType,
    fileName: CstString?,
    methodName: String,
    lineNumbers: Set<Int>
): EncodedMethod {
    val regCount = if (lineNumbers.isNotEmpty()) 8 else 7
    val finisher = OutputFinisher(dexOptions, 0, regCount, 2)

    val lineNumbersIntList = IntList(lineNumbers.size).apply {
        lineNumbers.asSequence().sorted().forEach { lineNumber ->
            add(lineNumber)
        }
        setImmutable()
    }

    val continueLabel = CodeAddress(SourcePosition.NO_INFO)
    finisher.gotoIfDepthValid(regCount, continueLabel)
    finisher.add(continueLabel)

    finisher.saveNextSpecAndResumeState(regCount)
    val resumeLabel = CodeAddress(SourcePosition.NO_INFO)
    finisher.gotoIfNextSpecMissing(resumeLabel)
    finisher.callNextSpec(
        fileName = fileName,
        lineNumbers = lineNumbersIntList,
        regCount = regCount
    )
    finisher.add(resumeLabel)
    finisher.resumeNext(
        fileName = fileName,
        lineNumbers = lineNumbersIntList,
        regCount = regCount
    )
    finisher.add(SimpleInsn(
        Dops.RETURN_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(resultRegister(regCount))
    ))

    val code = DalvCode(
        PositionList.LINES,
        finisher,
        object: CatchBuilder {
            override fun build() = CatchTable.EMPTY
            override fun hasAnyCatches() = false
            override fun getCatchTypes() = error("something wrong")
        }
    )

    return EncodedMethod(
        CstMethodRef(clazz, CstNat(CstString(methodName), specMethodDesc)),
        Modifier.PUBLIC or Modifier.STATIC or Modifier.FINAL,
        code,
        StdTypeList.EMPTY
    )
}

private val specClass = Type.internClassName(DecoroutinatorSpec::class.java.name.internalName)
@Suppress("NewApi")
private val methodHandleClass = Type.internClassName(MethodHandle::class.java.name.internalName)
private val checksumFailedMarkerClass = Type.internClassName(ChecksumFailedMarker::class.java.name.internalName)

private val specMethodDesc = CstString(
    "(${specClass.descriptor}${Type.OBJECT.descriptor}${Type.INT.descriptor}${Type.INT.descriptor})${Type.OBJECT.descriptor}"
)
private val aux0MethodHandle = RegisterSpec.make(0, methodHandleClass)
private val aux1Spec = RegisterSpec.make(1, specClass)
// Live for the WHOLE method (needed by the final `resume` call at the very end) - unlike
// aux0MethodHandle/aux1Spec, always present regardless of regCount.
private val auxResumeCookie = RegisterSpec.make(2, Type.OBJECT)
// Only live when regCount == 8 (lineNumbers is non-empty) - callNextSpec's switch runs while
// aux0MethodHandle/aux1Spec are still needed for the invokeExact call that follows, so its line number
// needs its own register (3).
private val aux3LineNumber = RegisterSpec.make(3, Type.INT)
// Only live when regCount == 8 - resumeNext's switch runs after aux0MethodHandle/aux1Spec are dead (the
// "invoke next spec" work, if any, has already happened by then), so it's safe to reuse register 0
// rather than spend a further register on a second line-number slot.
private val aux0LineNumber = RegisterSpec.make(0, Type.INT)

// spec/result/resumeChecksum/depthChecksum are the method's own four parameters, always the LAST four
// registers - immediately after aux0MethodHandle/aux1Spec/auxResumeCookie (and, when present, the
// line-number register). buildSpecMethod sizes regCount down by one (skipping the dedicated line-number
// register) when `lineNumbers` is empty, since instructionsByLineNumbers then never dispatches on a line
// number at all (see its own early return) - so spec/result/resumeChecksum/depthChecksum shift down by
// one register in that case.
private fun specRegister(regCount: Int) =
    RegisterSpec.make(regCount - 4, specClass)

private fun resultRegister(regCount: Int) =
    RegisterSpec.make(regCount - 3, Type.OBJECT)

private fun resumeChecksumRegister(regCount: Int) =
    RegisterSpec.make(regCount - 2, Type.INT)

private fun depthChecksumRegister(regCount: Int) =
    RegisterSpec.make(regCount - 1, Type.INT)

// RegisterSpecList only ships `make` overloads up to 4 elements; both callNextSpec's invoke-polymorphic
// call and resumeNext's $decoroutinator$resume call now need 5 (the depthChecksum parameter pushed them
// past 4), so build the list directly via the public size-constructor + set, same as `make` itself does
// internally.
private fun makeRegisterSpecList(vararg specs: RegisterSpec): RegisterSpecList {
    val result = RegisterSpecList(specs.size)
    specs.forEachIndexed { index, spec ->
        result.set(index, spec)
    }
    return result
}

// Depth guard: bounds the recursive descent through the spec chain so a structurally-cyclic chain (e.g.
// from a shared spec holder racily reused by two unrelated logical resumptions - see CLAUDE.md's
// resumeChecksum entry) fails cleanly instead of recursing until the real call stack overflows.
// depthChecksumRegister is seeded to the exact chain length when the chain is built (common/awakener.kt's
// buildSpecChain), decremented by exactly 1 on every dispatch into a spec method; a well-formed,
// non-cyclic chain never drives it negative, so this per-dispatch entry check alone is sufficient to
// catch a runaway cycle - resume() (utils-provider.kt) does NOT also re-check depthChecksum once dispatch
// reaches it, deliberately: BaseContinuationImpl.resumeWith's contract must still run exactly once even
// if depthChecksum isn't exactly DEPTH_VALID by then for some non-cyclic reason, and resumeChecksum
// already independently catches the actual corruption class this whole mechanism exists for. ADD_INT_LIT8
// decrements the parameter register in place (dex analogue
// of the JVM generator's IINC - no separate "updated" register, generated bytecode has no restriction
// against reassigning a parameter register, unlike unknown()'s Kotlin source). DEPTH_VALID is inlined as
// the literal comparand 0 via IF_GEZ, never loaded via reflection or an extra provider call.
@Suppress("NewApi")
private fun OutputFinisher.gotoIfDepthValid(regCount: Int, label: CodeAddress) {
    add(CstInsn(
        Dops.ADD_INT_LIT8,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(depthChecksumRegister(regCount), depthChecksumRegister(regCount)),
        CstInteger.make(-1)
    ))
    add(TargetInsn(
        Dops.IF_GEZ,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(depthChecksumRegister(regCount)),
        label
    ))
    add(CstInsn(
        Dops.INVOKE_INTERFACE,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(specRegister(regCount)),
        CstMethodRef(
            CstType(specClass),
            CstNat(
                CstString(checksumFailedMarkerMethodName),
                CstString("()${checksumFailedMarkerClass.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(resultRegister(regCount))
    ))
    add(SimpleInsn(
        Dops.RETURN_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(resultRegister(regCount))
    ))
}

// spec.$decoroutinator$resumeCookie/$decoroutinator$getResumeChecksum()/$decoroutinator$nextSpecHandle/
// $decoroutinator$nextSpec are plain (no longer one-shot) getters - a spec object can now be shared/
// reused across concurrent resumptions (see provider-api.kt's SharedSpec and CLAUDE.md's resumeChecksum
// entry), so each is read exactly once here and reused from auxResumeCookie/resumeChecksumRegister/
// aux0MethodHandle/aux1Spec from here on, guaranteeing every use within this single spec method
// invocation sees the same snapshot even if another thread concurrently re-`$decoroutinator$init`s the
// same shared spec.
@Suppress("NewApi")
private fun OutputFinisher.saveNextSpecAndResumeState(regCount: Int) {
    add(CstInsn(
        Dops.INVOKE_INTERFACE,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(specRegister(regCount)),
        CstMethodRef(
            CstType(specClass),
            CstNat(
                CstString(resumeCookieMethodName),
                CstString("()${Type.OBJECT.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(auxResumeCookie)
    ))
    add(CstInsn(
        Dops.INVOKE_INTERFACE,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(specRegister(regCount), resumeChecksumRegister(regCount), auxResumeCookie),
        CstMethodRef(
            CstType(specClass),
            CstNat(
                CstString(resumeChecksumMethodName),
                CstString("(${Type.INT.descriptor}${Type.OBJECT.descriptor})${Type.INT.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(resumeChecksumRegister(regCount))
    ))
    add(CstInsn(
        Dops.INVOKE_INTERFACE,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(specRegister(regCount)),
        CstMethodRef(
            CstType(specClass),
            CstNat(
                CstString(nextSpecHandleMethodName),
                CstString("()${methodHandleClass.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux0MethodHandle)
    ))
    add(CstInsn(
        Dops.INVOKE_INTERFACE,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(specRegister(regCount)),
        CstMethodRef(
            CstType(specClass),
            CstNat(
                CstString(nextSpecMethodName),
                CstString("()${specClass.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux1Spec)
    ))
}

// Both must be non-null to call the next spec method; either being null means this is the last spec in
// the chain, so skip straight to resuming this spec's own continuation.
private fun OutputFinisher.gotoIfNextSpecMissing(label: CodeAddress) {
    add(TargetInsn(
        Dops.IF_EQZ,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux0MethodHandle),
        label
    ))
    add(TargetInsn(
        Dops.IF_EQZ,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux1Spec),
        label
    ))
}

// Mirrors mh-invoker's unknown() fallback exactly: invoke the next spec method passing the current
// resumeChecksum/depthChecksum along, store the (possibly ChecksumFailedMarker) result back into
// resultRegister - Kotlin's `updatedResult`/`updatedResumeChecksum`/`updatedDepthChecksum` locals only
// exist because Kotlin won't let `unknown()` reassign its own value parameters; generated bytecode has no
// such restriction, so this reuses the method's own result/resumeChecksum/depthChecksum parameter
// registers directly - then unconditionally resets both resumeChecksum and depthChecksum back to their
// valid sentinels (both inlined as literal 0 here via Dops.CONST_4, never loaded via reflection or an
// extra provider call). The reset is unconditional - not gated on whether the nested call actually
// succeeded - because the shared `resume()` helper (utils-provider.kt) already short-circuits on
// `result.isChecksumFailedMarker` before it ever looks at resumeChecksum/depthChecksum, so a failed
// nested call still propagates correctly as ChecksumFailedMarker regardless of what these two get reset
// to; checking first (as this used to, via the now-removed `$decoroutinator$isChecksumFailedMarker`) was
// redundant.
@Suppress("NewApi")
private fun OutputFinisher.callNextSpec(
    fileName: CstString?,
    lineNumbers: IntList,
    regCount: Int
) {
    instructionsByLineNumbers(
        fileName = fileName,
        lineNumberRegister = aux3LineNumber,
        lineNumbers = lineNumbers,
        regCount = regCount
    ) { sourcePosition ->
        add(MultiCstInsn(
            Dops.INVOKE_POLYMORPHIC,
            sourcePosition,
            makeRegisterSpecList(aux0MethodHandle, aux1Spec, resultRegister(regCount), resumeChecksumRegister(regCount), depthChecksumRegister(regCount)),
            arrayOf(
                CstMethodRef(
                    CstType(Type.METHOD_HANDLE),
                    CstNat(
                        CstString(MethodHandle::invokeExact.name),
                        CstString("(${Type.OBJECT_ARRAY.descriptor})${Type.OBJECT.descriptor}")
                    )
                ),
                CstProtoRef.make(CstString(
                    "(${specClass.descriptor}${Type.OBJECT.descriptor}${Type.INT.descriptor}${Type.INT.descriptor})${Type.OBJECT.descriptor}"
                ))
            )
        ))
        add(SimpleInsn(
            Dops.MOVE_RESULT_OBJECT,
            sourcePosition,
            RegisterSpecList.make(resultRegister(regCount))
        ))
    }

    // CONST_4 (register + 4-bit signed literal both fit in one byte) always fits here - regCount never
    // exceeds 8 and the literal is always 0.
    add(CstInsn(
        Dops.CONST_4,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(resumeChecksumRegister(regCount)),
        CstInteger.VALUE_0
    ))
    add(CstInsn(
        Dops.CONST_4,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(depthChecksumRegister(regCount)),
        CstInteger.VALUE_0
    ))
}

private fun OutputFinisher.resumeNext(
    fileName: CstString?,
    lineNumbers: IntList,
    regCount: Int
) {
    instructionsByLineNumbers(
        fileName = fileName,
        lineNumbers = lineNumbers,
        lineNumberRegister = aux0LineNumber,
        regCount = regCount
    ) { sourcePosition ->
        add(CstInsn(
            Dops.INVOKE_INTERFACE,
            sourcePosition,
            makeRegisterSpecList(specRegister(regCount), resultRegister(regCount), resumeChecksumRegister(regCount), depthChecksumRegister(regCount), auxResumeCookie),
            CstMethodRef(
                CstType(specClass),
                CstNat(
                    CstString(resumeMethodName),
                    CstString("(${Type.OBJECT.descriptor}${Type.INT.descriptor}${Type.INT.descriptor}${Type.OBJECT.descriptor})${Type.OBJECT.descriptor}")
                )
            )
        ))
        add(SimpleInsn(
            Dops.MOVE_RESULT_OBJECT,
            sourcePosition,
            RegisterSpecList.make(resultRegister(regCount))
        ))
    }
}

// Builds a switch over `lineNumbers`, one case per known line (each carrying its own source position
// so a stack trace captured while executing that case reports the original source line), plus an
// always-present default case running the exact same `addInstructions` (with a no-real-line
// SourcePosition) - reached whenever the runtime line doesn't match any known case, rather than
// throwing. When `lineNumbers` is empty there's nothing to dispatch on, so
// `spec.$decoroutinator$lineNumber` is skipped entirely and `addInstructions` just runs directly -
// mirroring spec-method-builder.kt's equivalent (JVM/ASM) `addByLineNumbers`. Otherwise the line number
// is read fresh right here (it's no longer a one-shot getter, see provider-api.kt's
// `$decoroutinator$lineNumber`, and this same value is needed again by the sibling switch built for
// the other half of the spec method body). dex's PACKED_SWITCH/SPARSE_SWITCH falls through to whatever
// instruction physically follows it on a miss - the default case's own instructions are placed right
// there (no separate label/GOTO indirection needed, unlike the labeled cases below, which the switch
// can only reach by jumping to their own label). Every case - default included - needs its own GOTO
// past the remaining cases, except the physically last labeled case, which already sits right next to
// `endLabel`.
private fun OutputFinisher.instructionsByLineNumbers(
    fileName: CstString?,
    lineNumbers: IntList,
    lineNumberRegister: RegisterSpec,
    regCount: Int,
    addInstructions: OutputFinisher.(SourcePosition) -> Unit
) {
    if (lineNumbers.size() == 0) {
        addInstructions(SourcePosition(fileName, -1, -1))
        return
    }

    add(CstInsn(
        Dops.INVOKE_INTERFACE,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(specRegister(regCount)),
        CstMethodRef(
            CstType(specClass),
            CstNat(
                CstString(specLineNumberMethodName),
                CstString("()${Type.INT.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(lineNumberRegister)
    ))

    val switchLabel = CodeAddress(SourcePosition.NO_INFO)
    val switchDataLabel = CodeAddress(SourcePosition.NO_INFO)
    val labels = Array(lineNumbers.size()) {
        CodeAddress(SourcePosition.NO_INFO)
    }
    val switchData = SwitchData(
        SourcePosition.NO_INFO,
        switchLabel,
        lineNumbers,
        labels
    )
    val endLabel = CodeAddress(SourcePosition.NO_INFO)

    //switch
    add(switchLabel)
    add(TargetInsn(
        if (switchData.isPacked) Dops.PACKED_SWITCH else Dops.SPARSE_SWITCH,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(lineNumberRegister),
        switchDataLabel
    ))

    //default
    addInstructions(SourcePosition(fileName, -1, -1))
    add(TargetInsn(
        Dops.GOTO,
        SourcePosition.NO_INFO,
        RegisterSpecList.EMPTY,
        endLabel
    ))

    // switch data
    add(OddSpacer(SourcePosition.NO_INFO))
    add(switchDataLabel)
    add(switchData)

    (0 until lineNumbers.size()).forEach { index ->
        add(labels[index])
        addInstructions(SourcePosition(fileName, -1, lineNumbers[index]))
        if (index < lineNumbers.size() - 1) {
            add(TargetInsn(
                Dops.GOTO,
                SourcePosition.NO_INFO,
                RegisterSpecList.EMPTY,
                endLabel
            ))
        }
    }

    add(endLabel)
}
