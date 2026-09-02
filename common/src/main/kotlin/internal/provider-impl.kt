@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.provider.internal.fillUnknownElementsWithClassName
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.intrinsics.UNKNOWN_LINE_NUMBER
import dev.reformator.stacktracedecoroutinator.provider.BaseContinuationExtractor
import dev.reformator.stacktracedecoroutinator.provider.ContinuationCached
import dev.reformator.stacktracedecoroutinator.provider.SpecCache
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider
import dev.reformator.stacktracedecoroutinator.provider.internal.baseContinuationAccessorProvider
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.CoroutineStackFrame
import java.lang.invoke.MethodHandles
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class Provider: DecoroutinatorProvider {
    // One Provider instance is built per target class loader (see jvm-agent's
    // DispatchingProvider), so this cache is already correctly scoped there - it's process-wide
    // only in the single-Provider-instance ("common case") installation methods, which only ever
    // see one BaseContinuationImpl anyway.
    private val prepareBaseContinuationAccessorLock = ReentrantLock()

    private var _baseContinuationAccessor: BaseContinuationAccessor? = null

    override fun getBaseContinuationAccessor(baseContinuation: Any): BaseContinuationAccessor? =
        _baseContinuationAccessor

    @Suppress("NewApi")
    override fun prepareBaseContinuationAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor =
        prepareBaseContinuationAccessorLock.withLock {
            _baseContinuationAccessor?.let { return@withLock it }
            val accessor = baseContinuationAccessorProvider.createAccessor(lookup)
            _baseContinuationAccessor = accessor
            accessor
        }

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

    override fun callInvokeSuspendIfResultIsNotCoroutineSuspended(
        baseContinuation: Any,
        accessor: BaseContinuationAccessor,
        result: Any?
    ): Any? =
        if (result !== COROUTINE_SUSPENDED) {
            (baseContinuation as BaseContinuation).callInvokeSuspend(accessor, result)
        } else {
            result
        }
}

@OptIn(ExperimentalContracts::class)
internal inline fun CoroutineStackFrame.getOptionalSpecCacheAndStacktraceElement(
    consumer: (SpecCache?, StackTraceElement?) -> Unit
) {
    contract {
        callsInPlace(consumer, InvocationKind.EXACTLY_ONCE)
    }

    (this as? ContinuationCached)?.`$decoroutinator$cache`?.let { specCache ->
        consumer(specCache, specCache.element)
        return
    }

    val element = getStackTraceElement()
    val normalizedElement = when {
        element != null -> element
        fillUnknownElementsWithClassName -> {
            StackTraceElement(
                javaClass.name,
                Continuation<*>::resumeWith.name,
                null,
                UNKNOWN_LINE_NUMBER
            )
        }
        else -> null
    }

    consumer(null, normalizedElement)
}
