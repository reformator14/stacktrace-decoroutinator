@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.DebugMetadata
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.intrinsics.LABEL_FIELD_NAME
import dev.reformator.stacktracedecoroutinator.intrinsics.UNKNOWN_LINE_NUMBER
import dev.reformator.stacktracedecoroutinator.intrinsics.ifAssertionEnabled
import dev.reformator.stacktracedecoroutinator.provider.internal.KotlinDebugMetadata
import dev.reformator.stacktracedecoroutinator.provider.internal.annotationMetadataResolver
import dev.reformator.stacktracedecoroutinator.provider.internal.getBodyStream
import dev.reformator.stacktracedecoroutinator.provider.internal.optimisticLockGetOrPut
import dev.reformator.stacktracedecoroutinator.provider.internal.supportsVarHandle
import dev.reformator.stacktracedecoroutinator.provider.internal.transformedClassesRegistry
import dev.reformator.stacktracedecoroutinator.provider.internal.varHandleInvoker
import java.lang.invoke.VarHandle
import java.lang.reflect.Field
import java.lang.reflect.GenericSignatureFormatError
import java.util.concurrent.locks.ReentrantLock

internal class StacktraceElementsFactoryImpl: StacktraceElementsFactory {
    private val specsByBaseContinuationClass: MutableMap<Class<*>, BaseContinuationClassSpec> = HashMap()
    private val updateLock = ReentrantLock()

    private fun getBaseContinuationClassSpec(baseContinuationClass: Class<*>): BaseContinuationClassSpec =
        specsByBaseContinuationClass.optimisticLockGetOrPut(
            key = baseContinuationClass,
            lock = updateLock
        ) { BaseContinuationClassSpec(baseContinuationClass) }

    override fun getStacktraceElement(baseContinuation: BaseContinuation): StackTraceElement? {
        val spec = getBaseContinuationClassSpec(baseContinuation.javaClass)
        val elementsByLabel = spec.elementsByLabel ?: return null
        val label = spec.getLabel(baseContinuation)
        if (label == UNKNOWN_LABEL) return elementsByLabel[0]
        return elementsByLabel[label and Int.MAX_VALUE]
    }

    override fun getLabel(baseContinuation: BaseContinuation): Int =
        getBaseContinuationClassSpec(baseContinuation.javaClass).getLabel(baseContinuation)

    private class BaseContinuationClassSpec(baseContinuationClass: Class<*>) {
        @Suppress("JoinDeclarationAndAssignment")
        private val _elementsByLabel: Array<StackTraceElement>?
        private val labelReflectionField: Field?
        private val labelVarHandle: Any?

        init {
            _elementsByLabel = baseContinuationClass.getElementsByLabel()
            labelReflectionField = if (_elementsByLabel != null) {
                baseContinuationClass.getLabelReflectionField()
            } else null
            labelVarHandle = if (
                _elementsByLabel != null && _elementsByLabel !== failedElementsByLabel && supportsVarHandle
            ) {
                baseContinuationClass.getLabelVarHandle(_elementsByLabel[0].className)
            } else null
        }

        val elementsByLabel: Array<StackTraceElement>?
            get() = _elementsByLabel.takeIf { it !== failedElementsByLabel }

        fun getLabel(baseContinuation: BaseContinuation): Int {
            if (_elementsByLabel == null) return NONE_LABEL
            labelVarHandle?.let { labelVarHandle ->
                try {
                    @Suppress("NewApi")
                    return varHandleInvoker.getIntVar(labelVarHandle as VarHandle, baseContinuation)
                } catch (_: Throwable) { }
            }
            labelReflectionField?.let { labelReflectionField ->
                try {
                    return labelReflectionField[baseContinuation] as Int
                } catch (_: Throwable) { }
            }
            return UNKNOWN_LABEL
        }
    }
}

private val failedElementsByLabel = emptyArray<StackTraceElement>()

private fun Class<*>.getElementsByLabel(): Array<StackTraceElement>? {
    val (debugMeta, debugMetaSucceed) = try {
        val meta = getAnnotation(DebugMetadata::class.java)?.let { meta ->
            KotlinDebugMetadata(
                sourceFile = meta.fileName,
                className = meta.className,
                methodName = meta.methodName,
                lineNumbers = meta.lineNumbers
            )
        }
        meta to true
        // https://youtrack.jetbrains.com/issue/KT-25337
    } catch (_: GenericSignatureFormatError) {
        annotationMetadataResolver?.let { annotationMetadataResolver ->
            try {
                getBodyStream()?.use { classBody ->
                    annotationMetadataResolver.getKotlinDebugMetadata(classBody) to true
                }
            } catch (_: Exception) {
                null
            }
        } ?: (null to false)
    }
    ifAssertionEnabled {
        if (!debugMetaSucceed) require(debugMeta == null)
    }

    return if (debugMetaSucceed) {
        debugMeta?.let { meta ->
            Array(meta.lineNumbers.size + 1) { index ->
                StackTraceElement(
                    meta.className,
                    meta.methodName,
                    meta.sourceFile.ifEmpty { null },
                    if (index == 0) UNKNOWN_LINE_NUMBER else meta.lineNumbers[index - 1]
                )
            }
        }
    } else failedElementsByLabel
}

private fun Class<*>.getLabelReflectionField(): Field? =
    try {
        getDeclaredField(LABEL_FIELD_NAME).apply {
            isAccessible = true
        }
    } catch (_: Throwable) { null }

private fun Class<*>.getLabelVarHandle(elementClassName: String): Any? {
    return try {
        val spec = transformedClassesRegistry[Class.forName(elementClassName)] ?: return null
        @Suppress("NewApi")
        spec.lookup.findVarHandle(
            this,
            LABEL_FIELD_NAME,
            Int::class.javaPrimitiveType
        )
    } catch (_: Throwable) { null }
}
