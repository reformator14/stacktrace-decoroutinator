@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.classtransformer.internal

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.kmetarepack.isInline
import dev.reformator.kmetarepack.isSuspend
import dev.reformator.kmetarepack.jvm.JvmMethodSignature
import dev.reformator.kmetarepack.jvm.KotlinClassMetadata
import dev.reformator.kmetarepack.jvm.signature
import dev.reformator.kmetarepack.jvm.Metadata as createMetadata
import dev.reformator.stacktracedecoroutinator.intrinsics.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.intrinsics.LABEL_FIELD_NAME
import dev.reformator.stacktracedecoroutinator.intrinsics.UNKNOWN_LINE_NUMBER
import dev.reformator.stacktracedecoroutinator.intrinsics.assert
import dev.reformator.stacktracedecoroutinator.provider.BaseContinuationExtractor
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpecImpl
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpecMethod
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import dev.reformator.stacktracedecoroutinator.provider.LazilyCachedContinuation
import dev.reformator.stacktracedecoroutinator.provider.ManualContinuation
import dev.reformator.stacktracedecoroutinator.provider.SpecCache
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.binaryName
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
import dev.reformator.stacktracedecoroutinator.provider.internal.providerInternalApiClass
import dev.reformator.stacktracedecoroutinator.provider.providerApiClass
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.buildSpecMethodNode
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.decoroutinatorTransformedAnnotation
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.getClassNode
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.getField
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.kotlinDebugMetadataAnnotation
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.io.InputStream
import java.lang.invoke.MethodHandles
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.CoroutineStackFrame
import kotlin.jvm.java

class ClassBodyTransformationStatus(
    val updatedBody: ByteArray?,
    val needReadProviderModule: Boolean
)

fun transformClassBody(
    classBody: InputStream,
    classBodyResolver: (className: String) -> InputStream?,
    mode: DecoroutinatorTransformed.Mode
): ClassBodyTransformationStatus {
    val node = getClassNode(classBody) ?: return noClassBodyTransformationStatus
    node.decoroutinatorTransformedAnnotation?.let { transformedAnnotation ->
        val currentMode = run {
            @Suppress("UNCHECKED_CAST", "UnsafeCastWithReturn")
            val values = transformedAnnotation.getField(decoroutinatorTransformedModeMethodName) as Array<String>?
                ?: return@run DecoroutinatorTransformed.Mode.FULL
            DecoroutinatorTransformed.Mode.valueOf(values[1])
        }
        if (currentMode.ordinal > mode.ordinal) {
            error("Class '${node.name.binaryName}' is already transformed with weaker mode '$currentMode', " +
                    "cannot transform with stronger mode '$mode'")
        }
        return readProviderClassBodyTransformationStatus
    }

    var doTransformation = false
    val lineNumbersBySpecMethodName: MutableMap<String, MutableSet<Int>> = HashMap()
    val tailCallCaches: MutableList<TailCallDeoptimizeMethodNameAndLineNumber> = ArrayList()
    var preserveClassLayout = false
    var skipSpecMethods = false

    val metadataAnnotation = node.kotlinMetadataAnnotation ?: return noClassBodyTransformationStatus
    val xi = metadataAnnotation.getField("xi") as Int?
    // 7th bit of 'xi' indicates that the class is a scope of an inline function
    if (xi == null || xi and (1 shl 7) == 0) {
        if (node.name == BASE_CONTINUATION_CLASS_NAME.internalName) {
            node.transformBaseContinuation()
            if (mode.allowChangingClassLayout) {
                @Suppress("AssertionSideEffect") assert(node.trySetSpecImplAsBaseClass(true))
            } else {
                preserveClassLayout = true
            }
            doTransformation = true
        } else {
            if (
                node.tryAddBaseContinuationExtractor(mode.allowChangingClassLayout) ||
                node.tryAddManualContinuation(mode.allowChangingClassLayout, lineNumbersBySpecMethodName) ||
                node.tryAddLazilyCachedContinuation(mode.allowChangingClassLayout)
            ) {
                if (mode.allowChangingClassLayout) {
                    doTransformation = true
                    node.trySetSpecImplAsBaseClass(true)
                } else {
                    preserveClassLayout = true
                }
            }

            if (
                node.name in specHoldersInternalClassNames &&
                node.trySetSpecImplAsBaseClass(mode.allowChangingClassLayout)
            ) {
                if (mode.allowChangingClassLayout) {
                    doTransformation = true
                } else {
                    preserveClassLayout = true
                }
            }

            @Suppress("UNCHECKED_CAST")
            val notSuspendFunctionSignatures = createMetadata(
                kind = metadataAnnotation.getField("k") as Int?,
                metadataVersion = (metadataAnnotation.getField("mv") as List<Int>?)?.toIntArray(),
                data1 = (metadataAnnotation.getField("d1") as List<String>?)?.toTypedArray(),
                data2 = (metadataAnnotation.getField("d2") as List<String>?)?.toTypedArray(),
                extraString = metadataAnnotation.getField("xs") as String?,
                packageName = metadataAnnotation.getField("pn") as String?,
                extraInt = xi
            ).getNonSuspendFunctionSignatures()

            if (node.tryTransformSuspendMethods(
                classBodyResolver = classBodyResolver,
                lineNumbersBySpecMethodName = lineNumbersBySpecMethodName,
                notSuspendFunctionSignatures = notSuspendFunctionSignatures,
                tailCallCaches = tailCallCaches,
                allowChangingClassLayout = mode.allowChangingClassLayout
            )) doTransformation = true
        }
    }
    return if (doTransformation) {
        if (tailCallCaches.isNotEmpty()) {
            if (mode.allowChangingClassLayout) {
                node.saveTailCallCaches(tailCallCaches)
            } else {
                preserveClassLayout = true
            }
        }

        if (lineNumbersBySpecMethodName.isNotEmpty()) {
            if (mode.allowSpecMethods) {
                node.generateSpecMethods(lineNumbersBySpecMethodName)
            } else {
                skipSpecMethods = true
            }
        }

        node.generateTransformAnnotation(
            addFileAndClassName = lineNumbersBySpecMethodName.isNotEmpty() && mode.allowSpecMethods,
            mode = when {
                preserveClassLayout -> DecoroutinatorTransformed.Mode.PRESERVE_CLASS_LAYOUT
                skipSpecMethods -> DecoroutinatorTransformed.Mode.SKIP_SPEC_METHODS
                else -> DecoroutinatorTransformed.Mode.FULL
            }
        )

        ClassBodyTransformationStatus(
            updatedBody = node.classBody,
            needReadProviderModule = true
        )
    } else noClassBodyTransformationStatus
}

private val manualContinuationsInternalClassNames =
    sequenceOf(
        "kotlinx.coroutines.internal.ScopeCoroutine",
        "kotlinx.coroutines.DispatchedCoroutine",
        "kotlinx.coroutines.flow.internal.SafeCollector",
        "kotlinx.coroutines.UndispatchedCoroutine",
        "kotlinx.coroutines.TimeoutCoroutine",
        "kotlinx.coroutines.SupervisorCoroutine",
        "kotlinx.coroutines.flow.internal.FlowCoroutine",
        "kotlinx.coroutines.internal.DispatchedContinuation",
        "kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsJvmKt\$createCoroutineUnintercepted$\$inlined\$createCoroutineFromSuspendFunction\$IntrinsicsKt__IntrinsicsJvmKt$1",
        "io.ktor.util.pipeline.SuspendFunctionGun\$continuation$1",
        "kotlinx.coroutines.flow.internal.StackFrameContinuation"
    ).map { it.internalName }.toHashSet()

private val lazilyCachedContinuationsInternalClassNames =
    sequenceOf(
        "kotlinx.coroutines.debug.internal.DebugProbesImpl\$CoroutineOwner"
    ).map { it.internalName }.toHashSet()

private val specHoldersInternalClassNames =
    sequenceOf(
        "kotlinx.coroutines.JobSupport"
    ).map { it.internalName }.toHashSet()

private const val baseContinuationCachesFieldName = "\$decoroutinator\$caches"
private const val manualContinuationCacheFieldName = "\$decoroutinator\$cache"
private const val manualContinuationClassFieldName = "\$decoroutinator\$class"
private const val lazilyCachedContinuationCacheFieldName = "\$decoroutinator\$cache"

private val DecoroutinatorTransformed.Mode.allowChangingClassLayout: Boolean
    get() = this != DecoroutinatorTransformed.Mode.PRESERVE_CLASS_LAYOUT

private val DecoroutinatorTransformed.Mode.allowSpecMethods: Boolean
    get() = this == DecoroutinatorTransformed.Mode.FULL

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private fun Metadata.getNonSuspendFunctionSignatures(): List<JvmMethodSignature> {
    val functions = when(val metadata = KotlinClassMetadata.readLenient(this)) {
        is KotlinClassMetadata.Class -> metadata.kmClass.functions
        is KotlinClassMetadata.FileFacade -> metadata.kmPackage.functions
        is KotlinClassMetadata.SyntheticClass -> metadata.kmLambda?.function?.let { listOf(it) } ?: emptyList()
        is KotlinClassMetadata.MultiFileClassPart -> metadata.kmPackage.functions
        is KotlinClassMetadata.MultiFileClassFacade -> emptyList()
        is KotlinClassMetadata.Unknown -> emptyList()
    }
    return functions.asSequence()
        .filter { it.isInline || !it.isSuspend }
        .mapNotNull { it.signature }
        .filter { it.descriptor.endsWith("${Type.getDescriptor(Continuation::class.java)})${Type.getDescriptor(Object::class.java)}") }
        .toList()
}

private fun ClassNode.tryAddBaseContinuationExtractor(apply: Boolean): Boolean {
    val debugMetadata = kotlinDebugMetadataAnnotation ?: return false

    if (
        isInterface || fields.orEmpty().all { field ->
            field.name != LABEL_FIELD_NAME
            || field.desc != Type.INT_TYPE.descriptor
            || field.access and Opcodes.ACC_STATIC != 0
        }
    ) return false

    if (!apply) return true

    interfaces = interfaces.orEmpty() + Type.getInternalName(BaseContinuationExtractor::class.java)

    fields = fields.orEmpty() + FieldNode(
        Opcodes.ASM9,
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC,
        baseContinuationCachesFieldName,
        Type.getDescriptor(Array<SpecCache>::class.java),
        null,
        null
    )

    methods = methods.orEmpty() + MethodNode(Opcodes.ASM9).apply {
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC
        name = baseContinuationExtractorGetLabelMethodName
        desc = "()${Type.INT_TYPE.descriptor}"
        instructions = InsnList().apply {
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(FieldInsnNode(
                Opcodes.GETFIELD,
                this@tryAddBaseContinuationExtractor.name,
                LABEL_FIELD_NAME,
                Type.INT_TYPE.descriptor
            ))
            add(InsnNode(Opcodes.IRETURN))
        }
    } + MethodNode(Opcodes.ASM9).apply {
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC
        name = baseContinuationExtractorGetCachesMethodName
        desc = "()${Type.getDescriptor(Array<SpecCache>::class.java)}"
        instructions = InsnList().apply {
            add(FieldInsnNode(
                Opcodes.GETSTATIC,
                this@tryAddBaseContinuationExtractor.name,
                baseContinuationCachesFieldName,
                Type.getDescriptor(Array<SpecCache>::class.java)
            ))
            add(InsnNode(Opcodes.ARETURN))
        }
    }

    getOrCreateClinitMethod().apply {
        val className = (debugMetadata.getField(debugMetadataClassNameMethodName) as String?).orEmpty()
        val methodName = (debugMetadata.getField(debugMetadataMethodNameMethodName) as String?).orEmpty()
        val fileName = (debugMetadata.getField(debugMetadataFileNameMethodName) as String?)?.takeIf { it.isNotEmpty() }
        @Suppress("UNCHECKED_CAST")
        val lineNumbers = (debugMetadata.getField(debugMetadataLineNumbersMethodName) as List<Int>?).orEmpty()
        instructions.insertBefore(instructions.first, InsnList().apply {
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(providerApiClass),
                isDecoroutinatorEnabledMethodName,
                "()${Type.BOOLEAN_TYPE.descriptor}"
            ))
            val disabledLabel = LabelNode()
            add(JumpInsnNode(Opcodes.IFEQ, disabledLabel))
            add(LdcInsnNode(lineNumbers.size + 1))
            add(TypeInsnNode(Opcodes.ANEWARRAY, Type.getInternalName(SpecCache::class.java)))
            repeat(lineNumbers.size + 1) { index ->
                add(InsnNode(Opcodes.DUP))
                add(LdcInsnNode(index))
                add(TypeInsnNode(Opcodes.NEW, Type.getInternalName(SpecCache::class.java)))
                add(InsnNode(Opcodes.DUP))
                add(LdcInsnNode(className))
                add(LdcInsnNode(methodName))
                add(if (fileName != null) LdcInsnNode(fileName) else InsnNode(Opcodes.ACONST_NULL))
                add(LdcInsnNode(if (index == 0) UNKNOWN_LINE_NUMBER else lineNumbers[index - 1]))
                add(MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    Type.getInternalName(SpecCache::class.java),
                    "<init>",
                    "(${Type.getDescriptor(String::class.java)}${Type.getDescriptor(String::class.java)}"
                            + "${Type.getDescriptor(String::class.java)}${Type.INT_TYPE.descriptor})${Type.VOID_TYPE.descriptor}"
                ))
                add(InsnNode(Opcodes.AASTORE))
            }
            add(FieldInsnNode(
                Opcodes.PUTSTATIC,
                this@tryAddBaseContinuationExtractor.name,
                baseContinuationCachesFieldName,
                Type.getDescriptor(Array<SpecCache>::class.java)
            ))
            add(disabledLabel)
            add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
        })
    }

    return true
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private fun ClassNode.tryAddManualContinuation(
    apply: Boolean,
    lineNumbersBySpecMethodName: MutableMap<String, MutableSet<Int>>
): Boolean {
    if (isInterface || name !in manualContinuationsInternalClassNames) return false

    if (!apply) return true

    interfaces = interfaces.orEmpty() + Type.getInternalName(ManualContinuation::class.java)

    fields = fields.orEmpty() + FieldNode(
        Opcodes.ASM9,
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC,
        manualContinuationCacheFieldName,
        Type.getDescriptor(SpecCache::class.java),
        null,
        null
    ) + FieldNode(
        Opcodes.ASM9,
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC,
        manualContinuationClassFieldName,
        Type.getDescriptor(Class::class.java),
        null,
        null
    )

    getOrCreateClinitMethod().apply {
        instructions.insertBefore(instructions.first, InsnList().apply {
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(providerApiClass),
                fillUnknownElementsWithClassNameMethodName,
                "()${Type.BOOLEAN_TYPE.descriptor}"
            ))
            val disabledLabel = LabelNode()
            add(JumpInsnNode(Opcodes.IFEQ, disabledLabel))
            add(TypeInsnNode(Opcodes.NEW, Type.getInternalName(SpecCache::class.java)))
            add(InsnNode(Opcodes.DUP))
            add(LdcInsnNode(this@tryAddManualContinuation.name.binaryName))
            add(LdcInsnNode(Continuation<*>::resumeWith.name))
            add(sourceFile.let { if (it != null) LdcInsnNode(it) else InsnNode(Opcodes.ACONST_NULL) })
            add(LdcInsnNode(UNKNOWN_LINE_NUMBER))
            add(MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                Type.getInternalName(SpecCache::class.java),
                "<init>",
                "(" +
                    "${Type.getDescriptor(String::class.java)}" +
                    "${Type.getDescriptor(String::class.java)}" +
                    "${Type.getDescriptor(String::class.java)}" +
                    "${Type.INT_TYPE.descriptor}" +
                ")${Type.VOID_TYPE.descriptor}"
            ))
            add(FieldInsnNode(
                Opcodes.PUTSTATIC,
                this@tryAddManualContinuation.name,
                manualContinuationCacheFieldName,
                Type.getDescriptor(SpecCache::class.java)
            ))
            add(LdcInsnNode(Type.getObjectType(this@tryAddManualContinuation.name)))
            add(FieldInsnNode(
                Opcodes.PUTSTATIC,
                this@tryAddManualContinuation.name,
                manualContinuationClassFieldName,
                Type.getDescriptor(Class::class.java)
            ))
            add(disabledLabel)
            add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
        })
    }

    methods = methods.orEmpty() + MethodNode(Opcodes.ASM9).apply {
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC
        name = manualContinuationGetCacheFieldMethodName
        desc = "()${Type.getDescriptor(SpecCache::class.java)}"
        instructions = InsnList().apply {
            add(FieldInsnNode(
                Opcodes.GETSTATIC,
                this@tryAddManualContinuation.name,
                manualContinuationCacheFieldName,
                Type.getDescriptor(SpecCache::class.java)
            ))
            add(InsnNode(Opcodes.ARETURN))
        }
    } + MethodNode(Opcodes.ASM9).apply {
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC
        name = manualContinuationGetClassFieldMethodName
        desc = "()${Type.getDescriptor(Class::class.java)}"
        instructions = InsnList().apply {
            add(FieldInsnNode(
                Opcodes.GETSTATIC,
                this@tryAddManualContinuation.name,
                manualContinuationClassFieldName,
                Type.getDescriptor(Class::class.java)
            ))
            add(InsnNode(Opcodes.ARETURN))
        }
    }

    updateGetStackTraceElementMethod(
        isUsingElementCacheForGetElementMethodEnabledMethodName = isUsingElementCacheForManualContinuationGetElementMethodEnabledMethodName,
        getSpecCacheMethodOwnerInterfaceClass = ManualContinuation::class.java,
        getSpecCacheMethodName = manualContinuationGetCacheMethodName
    )

    lineNumbersBySpecMethodName.computeIfAbsent(Continuation<*>::resumeWith.name) {
        hashSetOf(UNKNOWN_LINE_NUMBER)
    }.add(UNKNOWN_LINE_NUMBER)

    return true
}

private fun ClassNode.updateGetStackTraceElementMethod(
    isUsingElementCacheForGetElementMethodEnabledMethodName: String,
    getSpecCacheMethodOwnerInterfaceClass: Class<*>,
    getSpecCacheMethodName: String,
) {
    val getStackTraceElementMethod = methods.find { method ->
        method.name == CoroutineStackFrame::getStackTraceElement.name && !method.isStatic &&
        method.desc == "()${Type.getDescriptor(StackTraceElement::class.java)}" &&
        (method.instructions?.size() ?: 0) > 0
    }

    @Suppress("IfThenToSafeAccess")
    if (getStackTraceElementMethod != null) {
        getStackTraceElementMethod.instructions.insertBefore(
            getStackTraceElementMethod.instructions.first,
            InsnList().apply {
                add(MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(providerApiClass),
                    isUsingElementCacheForGetElementMethodEnabledMethodName,
                    "()${Type.BOOLEAN_TYPE.descriptor}"
                ))
                val disabledLabel = LabelNode()
                add(JumpInsnNode(Opcodes.IFEQ, disabledLabel))

                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    Type.getInternalName(getSpecCacheMethodOwnerInterfaceClass),
                    getSpecCacheMethodName,
                    "()${Type.getDescriptor(SpecCache::class.java)}"
                ))
                add(InsnNode(Opcodes.DUP))
                val cacheIsNullLabel = LabelNode()
                add(JumpInsnNode(Opcodes.IFNULL, cacheIsNullLabel))

                add(MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getInternalName(SpecCache::class.java),
                    specCacheGetElementMethodName,
                    "()${Type.getDescriptor(StackTraceElement::class.java)}"
                ))
                add(InsnNode(Opcodes.ARETURN))

                add(cacheIsNullLabel)
                add(FrameNode(
                    Opcodes.F_SAME1,
                    0,
                    null,
                    1,
                    arrayOf(Type.getInternalName(SpecCache::class.java))
                ))
                add(InsnNode(Opcodes.POP))
                add(disabledLabel)
                add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
            }
        )
    }
}

private fun ClassNode.tryAddLazilyCachedContinuation(apply: Boolean): Boolean {
    if (isInterface || name !in lazilyCachedContinuationsInternalClassNames) return false

    if (!apply) return true

    interfaces = interfaces.orEmpty() + Type.getInternalName(LazilyCachedContinuation::class.java)

    fields = fields.orEmpty() + FieldNode(
        Opcodes.ASM9,
        Opcodes.ACC_PRIVATE or Opcodes.ACC_SYNTHETIC,
        lazilyCachedContinuationCacheFieldName,
        Type.getDescriptor(SpecCache::class.java),
        null,
        null
    )

    methods = methods.orEmpty() + MethodNode(Opcodes.ASM9).apply {
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC
        name = lazilyCachedContinuationGetCacheFieldMethodName
        desc = "()${Type.getDescriptor(SpecCache::class.java)}"
        instructions = InsnList().apply {
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(FieldInsnNode(
                Opcodes.GETFIELD,
                this@tryAddLazilyCachedContinuation.name,
                lazilyCachedContinuationCacheFieldName,
                Type.getDescriptor(SpecCache::class.java)
            ))
            add(InsnNode(Opcodes.ARETURN))
        }
    } + MethodNode(Opcodes.ASM9).apply {
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC
        name = lazilyCachedContinuationSetCacheFieldMethodName
        desc = "(${Type.getDescriptor(SpecCache::class.java)})V"
        instructions = InsnList().apply {
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(VarInsnNode(Opcodes.ALOAD, 1))
            add(FieldInsnNode(
                Opcodes.PUTFIELD,
                this@tryAddLazilyCachedContinuation.name,
                lazilyCachedContinuationCacheFieldName,
                Type.getDescriptor(SpecCache::class.java)
            ))
            add(InsnNode(Opcodes.RETURN))
        }
    }

    updateGetStackTraceElementMethod(
        isUsingElementCacheForGetElementMethodEnabledMethodName = isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabledMethodName,
        getSpecCacheMethodOwnerInterfaceClass = LazilyCachedContinuation::class.java,
        getSpecCacheMethodName = lazilyCachedContinuationGetCacheFieldMethodName
    )

    return true
}

private val ClassNode.isInterface: Boolean
    get() = access and Opcodes.ACC_INTERFACE != 0

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private fun ClassNode.trySetSpecImplAsBaseClass(apply: Boolean): Boolean {
    if (isInterface) return false
    if (superName != Type.getInternalName(Object::class.java)) return false
    if (!apply) return true
    superName = Type.getInternalName(DecoroutinatorSpecImpl::class.java)
    methods.orEmpty().forEach { method ->
        if (method.name == "<init>") {
            val instructions = method.instructions
            if (instructions != null) {
                instructions.forEach { instruction ->
                    if (
                        instruction is MethodInsnNode &&
                        instruction.opcode == Opcodes.INVOKESPECIAL &&
                        instruction.name == "<init>" &&
                        instruction.owner == Type.getInternalName(Object::class.java)
                    ) {
                        instruction.owner = Type.getInternalName(DecoroutinatorSpecImpl::class.java)
                    }
                }
            }
        }
    }
    return true
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private fun ClassNode.transformBaseContinuation() {
    val resumeWithMethod = methods?.find {
        it.desc == "(${Type.getDescriptor(Object::class.java)})${Type.VOID_TYPE.descriptor}" && !it.isStatic
    } ?: error("[${BaseContinuation::resumeWith.name}] method is not found")
    resumeWithMethod.instructions.insertBefore(resumeWithMethod.instructions.first, InsnList().apply {
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerApiClass),
            isDecoroutinatorEnabledMethodName,
            "()${Type.BOOLEAN_TYPE.descriptor}"
        ))
        val defaultAwakeLabel = LabelNode()
        add(JumpInsnNode(
            Opcodes.IFEQ,
            defaultAwakeLabel
        ))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerInternalApiClass),
            getBaseContinuationAccessorMethodName,
            "()${Type.getDescriptor(BaseContinuationAccessor::class.java)}"
        ))
        add(InsnNode(Opcodes.DUP))
        val decoroutinatorAwakeLabel = LabelNode()
        add(JumpInsnNode(
            Opcodes.IFNONNULL,
            decoroutinatorAwakeLabel
        ))
        add(InsnNode(Opcodes.POP))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(MethodHandles::class.java),
            MethodHandles::lookup.name,
            "()${Type.getDescriptor(MethodHandles.Lookup::class.java)}"
        ))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerInternalApiClass),
            prepareBaseContinuationAccessorMethodName,
            "(${Type.getDescriptor(MethodHandles.Lookup::class.java)})${Type.getDescriptor(BaseContinuationAccessor::class.java)}"
        ))
        add(decoroutinatorAwakeLabel)
        add(FrameNode(Opcodes.F_SAME1, 0, null, 1, arrayOf(Type.getInternalName(Object::class.java))))
        add(VarInsnNode(Opcodes.ALOAD, 0))
        add(VarInsnNode(Opcodes.ALOAD, 1))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerInternalApiClass),
            awakeBaseContinuationMethodName,
            "(${Type.getDescriptor(BaseContinuationAccessor::class.java)}${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(Object::class.java)})${Type.VOID_TYPE.descriptor}"
        ))
        add(InsnNode(Opcodes.RETURN))
        add(defaultAwakeLabel)
        add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
    })

    val getStackTraceElementMethod = methods?.find {
        it.desc == "()${Type.getDescriptor(StackTraceElement::class.java)}" && !it.isStatic
    } ?: error("[${BaseContinuation::getStackTraceElement.name}] method is not found")
    getStackTraceElementMethod.instructions.insertBefore(
        getStackTraceElementMethod.instructions.first,
        InsnList().apply {
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(providerApiClass),
                isUsingElementFactoryForBaseContinuationEnabledMethodName,
                "()${Type.BOOLEAN_TYPE.descriptor}"
            ))
            val defaultLabel = LabelNode()
            add(JumpInsnNode(
                Opcodes.IFEQ,
                defaultLabel
            ))
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(providerInternalApiClass),
                getElementFactoryStacktraceElementMethodName,
                "(${Type.getDescriptor(Object::class.java)})${Type.getDescriptor(StackTraceElement::class.java)}"
            ))
            add(InsnNode(Opcodes.ARETURN))
            add(defaultLabel)
            add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
        }
    )
}

val noClassBodyTransformationStatus = ClassBodyTransformationStatus(
    updatedBody = null,
    needReadProviderModule = false
)

private val readProviderClassBodyTransformationStatus = ClassBodyTransformationStatus(
    updatedBody = null,
    needReadProviderModule = true
)

private fun ClassNode.generateSpecMethods(lineNumbersBySpecMethodName: Map<String, Set<Int>>) {
    assert { lineNumbersBySpecMethodName.isNotEmpty() }
    val isPrivateMethodsInInterfacesSupported = version >= Opcodes.V9
    val makePrivate = !isInterface || isPrivateMethodsInInterfacesSupported
    val makeFinal = !isInterface
    version = maxOf(version, Opcodes.V1_7)
    methods = methods.orEmpty() + lineNumbersBySpecMethodName.map { (methodName, lineNumbers) ->
        val specMethodNode = buildSpecMethodNode(
            methodName = methodName,
            lineNumbers = lineNumbers,
            makePrivate = makePrivate,
            makeFinal = makeFinal
        )
        specMethodNode.visibleAnnotations = specMethodNode.visibleAnnotations.orEmpty() +
            AnnotationNode(Opcodes.ASM9, Type.getDescriptor(DecoroutinatorSpecMethod::class.java)).apply {
                values = buildList {
                    add(decoroutinatorSpecMethodMethodNameMethodName)
                    add(methodName)

                    add(decoroutinatorSpecMethodLineNumbersMethodName)
                    add(lineNumbers.sorted())
                }
            }
        specMethodNode
    }
    getOrCreateClinitMethod().instructions.apply {
        insertBefore(first, buildCallRegisterLookupInstructions())
    }
}

private fun ClassNode.generateTransformAnnotation(addFileAndClassName: Boolean, mode: DecoroutinatorTransformed.Mode) {
    visibleAnnotations = visibleAnnotations.orEmpty() +
        AnnotationNode(Opcodes.ASM9, Type.getDescriptor(DecoroutinatorTransformed::class.java)).apply {
            values = buildList {
                if (addFileAndClassName) {
                    if (sourceFile != null) {
                        add(decoroutinatorTransformedFileNameMethodName)
                        add(sourceFile)
                    } else {
                        add(decoroutinatorTransformedFileNamePresentMethodName)
                        add(false)
                    }
                    add(decoroutinatorTransformedClassNameMethodName)
                    add(name.binaryName)
                }
                if (mode != DecoroutinatorTransformed.Mode.FULL) {
                    add(decoroutinatorTransformedModeMethodName)
                    add(arrayOf(
                        Type.getDescriptor(DecoroutinatorTransformed.Mode::class.java),
                        mode.name
                    ))
                }
            }
        }
}

private fun ClassNode.saveTailCallCaches(tailCallCaches: List<TailCallDeoptimizeMethodNameAndLineNumber>) {
    assert { tailCallCaches.isNotEmpty() }

    val fieldAccess = (if (isInterface) Opcodes.ACC_PUBLIC else Opcodes.ACC_PRIVATE) or Opcodes.ACC_STATIC or
            Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC

    fields = fields.orEmpty() + List(tailCallCaches.size) { index ->
        FieldNode(
            Opcodes.ASM9,
            fieldAccess,
            getTailCallCacheFieldName(index),
            Type.getDescriptor(SpecCache::class.java),
            null,
            null
        )
    }

    getOrCreateClinitMethod().apply {
        instructions.insertBefore(instructions.first, InsnList().apply {
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(providerApiClass),
                isTailCallDeoptimizationEnabledMethodName,
                "()${Type.BOOLEAN_TYPE.descriptor}"
            ))
            val tailCallDeoptimizationDisabledLabel = LabelNode()
            add(JumpInsnNode(Opcodes.IFEQ, tailCallDeoptimizationDisabledLabel))

            tailCallCaches.forEachIndexed { index, cache ->
                add(TypeInsnNode(Opcodes.NEW, Type.getInternalName(SpecCache::class.java)))
                add(InsnNode(Opcodes.DUP))
                add(TypeInsnNode(Opcodes.NEW, Type.getInternalName(StackTraceElement::class.java)))
                add(InsnNode(Opcodes.DUP))
                add(LdcInsnNode(Type.getObjectType(this@saveTailCallCaches.name).className))
                add(LdcInsnNode(cache.methodName))
                if (this@saveTailCallCaches.sourceFile != null) {
                    add(LdcInsnNode(this@saveTailCallCaches.sourceFile))
                } else {
                    add(InsnNode(Opcodes.ACONST_NULL))
                }
                add(LdcInsnNode(cache.lineNumber))
                add(MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    Type.getInternalName(StackTraceElement::class.java),
                    "<init>",
                    "(${Type.getDescriptor(String::class.java)}${Type.getDescriptor(String::class.java)}"
                            + "${Type.getDescriptor(String::class.java)}${Type.INT_TYPE.descriptor})${Type.VOID_TYPE.descriptor}"
                ))
                add(MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    Type.getInternalName(SpecCache::class.java),
                    "<init>",
                    "(${Type.getDescriptor(StackTraceElement::class.java)})${Type.VOID_TYPE.descriptor}"
                ))
                add(FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    this@saveTailCallCaches.name,
                    getTailCallCacheFieldName(index),
                    Type.getDescriptor(SpecCache::class.java)
                ))
            }

            add(tailCallDeoptimizationDisabledLabel)
            add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
        })
    }
}

private val ClassNode.kotlinMetadataAnnotation: AnnotationNode?
    get() = visibleAnnotations
        .orEmpty()
        .firstOrNull { it.desc == Type.getDescriptor(Metadata::class.java) }

private fun ClassNode.tryTransformSuspendMethods(
    classBodyResolver: (className: String) -> InputStream?,
    lineNumbersBySpecMethodName: MutableMap<String, MutableSet<Int>>,
    tailCallCaches: MutableList<TailCallDeoptimizeMethodNameAndLineNumber>,
    notSuspendFunctionSignatures: Collection<JvmMethodSignature>,
    allowChangingClassLayout: Boolean
): Boolean {
    var needTransformation = false

    debugMetadataInfo?.let { info ->
        if (info.specClassInternalClassName == name) {
            needTransformation = true
            lineNumbersBySpecMethodName.computeIfAbsent(info.methodName) {
                hashSetOf(UNKNOWN_LINE_NUMBER)
            }.addAll(info.lineNumbers)
        }
    }

    methods.orEmpty().forEach { method ->
        if (tryTransformSuspendMethod(
            clazz = this,
            method = method,
            notSuspendFunctionSignatures = notSuspendFunctionSignatures,
            classBodyResolver = classBodyResolver,
            lineNumbersBySpecMethodName = lineNumbersBySpecMethodName,
            tailCallCaches = tailCallCaches,
            allowChangingClassLayout = allowChangingClassLayout
        )) needTransformation = true
    }

    return needTransformation
}

private class TailCallDeoptimizeMethodNameAndLineNumber(val methodName: String, val lineNumber: Int)

private class DebugMetadataInfo(
    val specClassInternalClassName: String,
    val methodName: String,
    val lineNumbers: Set<Int>
)

private fun getTailCallCacheFieldName(index: Int): String =
    "\$decoroutinator\$tailCallDeoptimizeCache$$index"

@Suppress("UNCHECKED_CAST")
private val ClassNode.debugMetadataInfo: DebugMetadataInfo?
    get() = kotlinDebugMetadataAnnotation?.let { annotation ->
        val internalClassName = (annotation.getField(debugMetadataClassNameMethodName) as String).internalName
        val methodName = annotation.getField(debugMetadataMethodNameMethodName) as String
        val lineNumbers = (annotation.getField(debugMetadataLineNumbersMethodName) as List<Int>).toSet()
        if (lineNumbers.isEmpty()) {
            return null
        }
        DebugMetadataInfo(
            specClassInternalClassName = internalClassName,
            methodName = methodName,
            lineNumbers = lineNumbers
        )
    }

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private fun tryTransformSuspendMethod(
    clazz: ClassNode,
    method: MethodNode,
    notSuspendFunctionSignatures: Collection<JvmMethodSignature>,
    classBodyResolver: (className: String) -> InputStream?,
    lineNumbersBySpecMethodName: MutableMap<String, MutableSet<Int>>,
    tailCallCaches: MutableList<TailCallDeoptimizeMethodNameAndLineNumber>,
    allowChangingClassLayout: Boolean
): Boolean {
    if (method.instructions == null || method.instructions.size() == 0) return false

    if (notSuspendFunctionSignatures.any { it.name == method.name && it.descriptor == method.desc }) return false

    val completionIndex = run {
        val methodType = Type.getMethodType(method.desc)
        if (methodType.returnType != Type.getType(Object::class.java)) return false
        val arguments = methodType.argumentTypes
        if (arguments.isEmpty() || arguments.last() != Type.getType(Continuation::class.java)) return false
        (if (method.isStatic) 0 else 1) + arguments.asSequence()
            .take(arguments.size - 1)
            .sumOf { it.size }
    }

    run {
        val metadataList = method.instructions.asSequence()
            .filterIsInstance<TypeInsnNode>()
            .filter { it.opcode == Opcodes.INSTANCEOF }
            .map { Type.getObjectType(it.desc) }
            .filter { it.sort == Type.OBJECT }
            .mapNotNull { instanceofInstruction ->
                val classBody = classBodyResolver(instanceofInstruction.className) ?: return@mapNotNull null
                classBody.use { getClassNode(it, skipCode = true)?.debugMetadataInfo }
            }.toList()

        var result = false

        metadataList.asSequence()
            .filter { it.specClassInternalClassName == clazz.name }
            .forEach { metadata ->
                result = true
                lineNumbersBySpecMethodName.computeIfAbsent(metadata.methodName) {
                    hashSetOf(UNKNOWN_LINE_NUMBER)
                }.addAll(metadata.lineNumbers)
            }

        if (metadataList.isNotEmpty()) return result
    }

    val loadCompletionInstruction = run {
        var loadCompletionInstruction: VarInsnNode? = null
        method.instructions.asSequence()
            .filterIsInstance<VarInsnNode>()
            .forEach { instruction ->
                when (instruction.opcode) {
                    Opcodes.ISTORE, Opcodes.FSTORE, Opcodes.ASTORE -> {
                        if (instruction.`var` == completionIndex) return false
                    }

                    Opcodes.LSTORE, Opcodes.DSTORE -> {
                        if (instruction.`var` == completionIndex || instruction.`var` == completionIndex - 1) {
                            return false
                        }
                    }

                    Opcodes.ALOAD -> {
                        if (instruction.`var` == completionIndex) {
                            if (loadCompletionInstruction == null) {
                                loadCompletionInstruction = instruction
                            } else return false
                        }
                    }
                }
            }
        loadCompletionInstruction ?: return false
    }

    run {
        val hasCompletionLocalVar = method.localVariables.orEmpty().any { localVar ->
            localVar.index == completionIndex && localVar.name == "\$completion"
        }
        if (!hasCompletionLocalVar) return false
    }

    run {
        val hasContinuationLocalVar = method.localVariables.orEmpty().any { localVar ->
            localVar.name == "\$continuation"
        }
        if (hasContinuationLocalVar) return false
    }

    run {
        val interfaceDefaultImpl =
            clazz.isInterface && method.isStatic && method.name.startsWith("defaultImpl$")
        if (method.isSynthetic && !interfaceDefaultImpl) return false
    }

    val lineNumber = generateSequence(loadCompletionInstruction.next) { it.next }
        .takeWhile { it.opcode == -1 }
        .filterIsInstance<LineNumberNode>()
        .firstOrNull()?.line
        ?: generateSequence(loadCompletionInstruction.previous) { it.previous }
            .filterIsInstance<LineNumberNode>()
            .firstOrNull()?.line
        ?: UNKNOWN_LINE_NUMBER

    val cacheFieldName = getTailCallCacheFieldName(tailCallCaches.size)
    tailCallCaches.add(TailCallDeoptimizeMethodNameAndLineNumber(method.name, lineNumber))
    lineNumbersBySpecMethodName.computeIfAbsent(method.name) {
        hashSetOf(UNKNOWN_LINE_NUMBER)
    }.add(lineNumber)

    method.instructions.insert(loadCompletionInstruction, InsnList().apply {
        if (allowChangingClassLayout) {
            add(FieldInsnNode(
                Opcodes.GETSTATIC,
                clazz.name,
                cacheFieldName,
                Type.getDescriptor(SpecCache::class.java)
            ))
        } else {
            add(TypeInsnNode(Opcodes.NEW, Type.getInternalName(SpecCache::class.java)))
            add(InsnNode(Opcodes.DUP))
            add(LdcInsnNode(clazz.name.binaryName))
            add(LdcInsnNode(method.name))
            add(if (clazz.sourceFile == null) InsnNode(Opcodes.ACONST_NULL) else LdcInsnNode(clazz.sourceFile))
            add(LdcInsnNode(lineNumber))
            add(MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                Type.getInternalName(SpecCache::class.java),
                "<init>",
                "(${Type.getDescriptor(String::class.java)}${Type.getDescriptor(String::class.java)}${Type.getDescriptor(String::class.java)}${Type.INT_TYPE.descriptor})${Type.VOID_TYPE.descriptor}"
            ))
        }
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerApiClass),
            tailCallDeoptimizeMethodName,
            "("
                    + Type.getDescriptor(Object::class.java)
                    + Type.getDescriptor(SpecCache::class.java)
                    + ")${Type.getDescriptor(Object::class.java)}"
        ))
        add(TypeInsnNode(Opcodes.CHECKCAST, Type.getInternalName(Continuation::class.java)))
    })

    return true
}

private val MethodNode.isStatic: Boolean
    get() = access and Opcodes.ACC_STATIC != 0

private val MethodNode.isSynthetic: Boolean
    get() = access and Opcodes.ACC_SYNTHETIC != 0

private fun ClassNode.getOrCreateClinitMethod(): MethodNode =
    methods?.firstOrNull {
        it.name == "<clinit>" && it.desc == "()${Type.VOID_TYPE.descriptor}" && it.isStatic
    } ?: MethodNode(Opcodes.ASM9).apply {
        name = "<clinit>"
        desc = "()${Type.VOID_TYPE.descriptor}"
        access = Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC
        instructions.add(InsnNode(Opcodes.RETURN))
        methods = methods.orEmpty() + this
    }

private val registerTransformedClassMethodName: String
    @LoadConstant("registerTransformedClassMethodName") get() = fail()

private fun buildCallRegisterLookupInstructions() = InsnList().apply {
    add(MethodInsnNode(
        Opcodes.INVOKESTATIC,
        Type.getInternalName(providerApiClass),
        isDecoroutinatorEnabledMethodName,
        "()${Type.BOOLEAN_TYPE.descriptor}"
    ))
    val disabledLabel = LabelNode()
    add(JumpInsnNode(
        Opcodes.IFEQ,
        disabledLabel
    ))
    add(MethodInsnNode(
        Opcodes.INVOKESTATIC,
        Type.getInternalName(MethodHandles::class.java),
        MethodHandles::lookup.name,
        "()${Type.getDescriptor(MethodHandles.Lookup::class.java)}"
    ))
    add(MethodInsnNode(
        Opcodes.INVOKESTATIC,
        Type.getInternalName(providerApiClass),
        registerTransformedClassMethodName,
        "(${Type.getDescriptor(MethodHandles.Lookup::class.java)})V"
    ))
    add(disabledLabel)
    add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
}

private val ClassNode.classBody: ByteArray
    get() {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        accept(writer)
        return writer.toByteArray()
    }

private val debugMetadataFileNameMethodName: String
    @LoadConstant("debugMetadataFileNameMethodName") get() = fail()

private val debugMetadataLineNumbersMethodName: String
    @LoadConstant("debugMetadataLineNumbersMethodName") get() = fail()

private val debugMetadataMethodNameMethodName: String
    @LoadConstant("debugMetadataMethodNameMethodName") get() = fail()

private val debugMetadataClassNameMethodName: String
    @LoadConstant("debugMetadataClassNameMethodName") get() = fail()

private val isDecoroutinatorEnabledMethodName: String
    @LoadConstant("isDecoroutinatorEnabledMethodName") get() = fail()

private val getBaseContinuationAccessorMethodName: String
    @LoadConstant("getBaseContinuationAccessorMethodName") get() = fail()

private val prepareBaseContinuationAccessorMethodName: String
    @LoadConstant("prepareBaseContinuationAccessorMethodName") get() = fail()

private val awakeBaseContinuationMethodName: String
    @LoadConstant("awakeBaseContinuationMethodName") get() = fail()

private val isUsingElementFactoryForBaseContinuationEnabledMethodName: String
    @LoadConstant("isUsingElementFactoryForBaseContinuationEnabledMethodName") get() = fail()

private val getElementFactoryStacktraceElementMethodName: String
    @LoadConstant("getElementFactoryStacktraceElementMethodName") get() = fail()

private val baseContinuationExtractorGetLabelMethodName: String
    @LoadConstant("baseContinuationExtractorGetLabelMethodName") get() = fail()

private val baseContinuationExtractorGetCachesMethodName: String
    @LoadConstant("baseContinuationExtractorGetCachesMethodName") get() = fail()

private val decoroutinatorTransformedFileNamePresentMethodName: String
    @LoadConstant("decoroutinatorTransformedFileNamePresentMethodName") get() = fail()

private val decoroutinatorTransformedFileNameMethodName: String
    @LoadConstant("decoroutinatorTransformedFileNameMethodName") get() = fail()

private val decoroutinatorTransformedClassNameMethodName: String
    @LoadConstant("decoroutinatorTransformedClassNameMethodName") get() = fail()

private val decoroutinatorTransformedModeMethodName: String
    @LoadConstant("decoroutinatorTransformedModeMethodName") get() = fail()

private val decoroutinatorSpecMethodMethodNameMethodName: String
    @LoadConstant("decoroutinatorSpecMethodMethodNameMethodName") get() = fail()

private val decoroutinatorSpecMethodLineNumbersMethodName: String
    @LoadConstant("decoroutinatorSpecMethodLineNumbersMethodName") get() = fail()

private val isTailCallDeoptimizationEnabledMethodName: String
    @LoadConstant("isTailCallDeoptimizationEnabledMethodName") get() = fail()

private val tailCallDeoptimizeMethodName: String
    @LoadConstant("tailCallDeoptimizeMethodName") get() = fail()

private val manualContinuationGetCacheFieldMethodName: String
    @LoadConstant("manualContinuationGetCacheFieldMethodName") get() = fail()

private val manualContinuationGetClassFieldMethodName: String
    @LoadConstant("manualContinuationGetClassFieldMethodName") get() = fail()

private val fillUnknownElementsWithClassNameMethodName: String
    @LoadConstant("fillUnknownElementsWithClassNameMethodName") get() = fail()

private val isUsingElementCacheForManualContinuationGetElementMethodEnabledMethodName: String
    @LoadConstant("isUsingElementCacheForManualContinuationGetElementMethodEnabledMethodName") get() = fail()

private val manualContinuationGetCacheMethodName: String
    @LoadConstant("manualContinuationGetCacheMethodName") get() = fail()

private val lazilyCachedContinuationGetCacheFieldMethodName: String
    @LoadConstant("lazilyCachedContinuationGetCacheFieldMethodName") get() = fail()

private val lazilyCachedContinuationSetCacheFieldMethodName: String
    @LoadConstant("lazilyCachedContinuationSetCacheFieldMethodName") get() = fail()

private val specCacheGetElementMethodName: String
    @LoadConstant("specCacheGetElementMethodName") get() = fail()

private val isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabledMethodName: String
    @LoadConstant("isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabledMethodName") get() = fail()
