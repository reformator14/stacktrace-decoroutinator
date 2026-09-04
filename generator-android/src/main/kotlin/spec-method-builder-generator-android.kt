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
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
import dev.reformator.stacktracedecoroutinator.provider.internal.nextSpecHandleMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.nextSpecMethodName
import dev.reformator.stacktracedecoroutinator.provider.internal.resumeNextMethodName
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
    val regCount = if (lineNumbers.isNotEmpty()) 5 else 4
    val finisher = OutputFinisher(dexOptions, 0, regCount, 2)

    val lineNumbersIntList = IntList(lineNumbers.size).apply {
        lineNumbers.asSequence().sorted().forEach { lineNumber ->
            add(lineNumber)
        }
        setImmutable()
    }

    finisher.saveNextSpec(regCount)
    val resumeNextLabel = CodeAddress(SourcePosition.NO_INFO)
    finisher.gotoIfNextSpecMissing(resumeNextLabel)
    finisher.callNextSpec(
        fileName = fileName,
        lineNumbers = lineNumbersIntList,
        regCount = regCount
    )
    finisher.add(resumeNextLabel)
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

private val specMethodDesc = CstString("(${specClass.descriptor}${Type.OBJECT.descriptor})${Type.OBJECT.descriptor}")
private val aux0MethodHandle = RegisterSpec.make(0, methodHandleClass)
private val aux1Spec = RegisterSpec.make(1, specClass)
// Only live when regCount == 5 (lineNumbers is non-empty) - callNextSpec's switch runs while
// aux0MethodHandle/aux1Spec are still needed for the invokeExact call that follows, so its line number
// needs its own register (2).
private val aux2LineNumber = RegisterSpec.make(2, Type.INT)
// Only live when regCount == 5 - resumeNext's switch runs after aux0MethodHandle/aux1Spec are dead (the
// "invoke next spec" work, if any, has already happened by then), so it's safe to reuse register 0
// rather than spend a further register on a second line-number slot.
private val aux0LineNumber = RegisterSpec.make(0, Type.INT)

// spec/result are the method's own two parameters, always the LAST two registers - immediately after
// aux0MethodHandle/aux1Spec (and, when present, the line-number register). buildSpecMethod sizes
// regCount down to 4 (skipping the dedicated line-number register) when `lineNumbers` is empty, since
// instructionsByLineNumbers then never dispatches on a line number at all (see its own early return) -
// so spec/result shift down by one register in that case.
private fun specRegister(regCount: Int) =
    RegisterSpec.make(regCount - 2, specClass)

private fun resultRegister(regCount: Int) =
    RegisterSpec.make(regCount - 1, Type.OBJECT)

// spec.$decoroutinator$getNextSpecHandle()/$decoroutinator$getNextSpec() are one-shot getters (they null
// out their backing field as they're read), so they're read exactly once here and reused from
// aux0MethodHandle/aux1Spec from here on - never re-invoked.
@Suppress("NewApi")
private fun OutputFinisher.saveNextSpec(regCount: Int) {
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

@Suppress("NewApi")
private fun OutputFinisher.callNextSpec(
    fileName: CstString?,
    lineNumbers: IntList,
    regCount: Int
) {
    instructionsByLineNumbers(
        fileName = fileName,
        lineNumberRegister = aux2LineNumber,
        lineNumbers = lineNumbers,
        regCount = regCount
    ) { sourcePosition ->
        add(MultiCstInsn(
            Dops.INVOKE_POLYMORPHIC,
            sourcePosition,
            RegisterSpecList.make(aux0MethodHandle, aux1Spec, resultRegister(regCount)),
            arrayOf(
                CstMethodRef(
                    CstType(Type.METHOD_HANDLE),
                    CstNat(
                        CstString(MethodHandle::invokeExact.name),
                        CstString("(${Type.OBJECT_ARRAY.descriptor})${Type.OBJECT.descriptor}")
                    )
                ),
                CstProtoRef.make(CstString(
                    "(${specClass.descriptor}${Type.OBJECT.descriptor})${Type.OBJECT.descriptor}"
                ))
            )
        ))
        add(SimpleInsn(
            Dops.MOVE_RESULT_OBJECT,
            sourcePosition,
            RegisterSpecList.make(resultRegister(regCount))
        ))
    }
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
            RegisterSpecList.make(specRegister(regCount), resultRegister(regCount)),
            CstMethodRef(
                CstType(specClass),
                CstNat(
                    CstString(resumeNextMethodName),
                    CstString("(${Type.OBJECT.descriptor})${Type.OBJECT.descriptor}")
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
// `spec.$decoroutinator$getLineNumber()` is skipped entirely and `addInstructions` just runs directly -
// mirroring spec-method-builder.kt's equivalent (JVM/ASM) `addByLineNumbers`. Otherwise the line number
// is read fresh right here (it's no longer a one-shot getter, see provider-api.kt's
// `$decoroutinator$getLineNumber`, and this same value is needed again by the sibling switch built for
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
