@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.provider.internal.fillUnknownElementsWithClassName
    as cachedFillUnknownElementsWithClassName
import dev.reformator.stacktracedecoroutinator.common.intrinsics.createFailure as intrinsicsCreateFailure
import dev.reformator.stacktracedecoroutinator.common.intrinsics.probeCoroutineResumed as intrinsicsProbeCoroutineResumed
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.intrinsics.UNKNOWN_LINE_NUMBER
import dev.reformator.stacktracedecoroutinator.provider.BaseContinuationExtractor
import dev.reformator.stacktracedecoroutinator.provider.ContinuationCached
import dev.reformator.stacktracedecoroutinator.provider.SpecCache
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.CoroutineStackFrame

internal class Provider: DecoroutinatorProvider {
    override fun awakeBaseContinuation(
        accessor: BaseContinuationAccessor,
        baseContinuation: Any,
        result: Any?
    ) {
        (baseContinuation as BaseContinuation).awake(accessor, result)
    }

    @Suppress("UNCHECKED_CAST")
    override fun tailCallDeoptimize(completion: Any, cache: SpecCache?): Any {
        if (cache == null) {
            return completion
        }
        if (completion is BaseContinuation) {
            val label =
                if (completion is BaseContinuationExtractor) {
                    completion.`$decoroutinator$label`
                } else {
                    stacktraceElementsFactory.getLabel(completion)
                }
            if (label != NONE_LABEL && label and Int.MIN_VALUE != 0) return completion
        }
        return TailCallDeoptimizedContinuation(completion as Continuation<Any?>, cache)
    }

    override fun getElementFactoryStacktraceElement(baseContinuation: Any): StackTraceElement? {
        (baseContinuation as? ContinuationCached)?.`$decoroutinator$cache`?.let { return it.element }
        return stacktraceElementsFactory.getStacktraceElement(baseContinuation as BaseContinuation)
    }

    override fun getCoroutineStackFrameStackTraceElement(coroutineStackFrame: Any): StackTraceElement? =
        (coroutineStackFrame as CoroutineStackFrame).getStackTraceElement()

    override val coroutineSuspendedMarker: Any
        get() = COROUTINE_SUSPENDED

    override fun probeCoroutineResumed(frameContinuation: Any) {
        intrinsicsProbeCoroutineResumed(frameContinuation as Continuation<*>)
    }

    override fun createFailure(exception: Throwable): Any =
        intrinsicsCreateFailure(exception)
}

internal fun CoroutineStackFrame.getNormalizedStackTraceElement(): StackTraceElement? {
    val element = getStackTraceElement()
    return when {
        element != null -> element
        cachedFillUnknownElementsWithClassName -> StackTraceElement(
            javaClass.name,
            Continuation<*>::resumeWith.name,
            null,
            UNKNOWN_LINE_NUMBER
        )
        else -> null
    }
}
