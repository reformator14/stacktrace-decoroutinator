@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.BaseContinuationExtractor
import dev.reformator.stacktracedecoroutinator.provider.ContinuationCached
import dev.reformator.stacktracedecoroutinator.provider.SpecCache
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider
import java.lang.invoke.MethodHandles
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.Continuation

internal class Provider: DecoroutinatorProvider {
    private val prepareBaseContinuationAccessorLock = ReentrantLock()
    private var _baseContinuationAccessor: BaseContinuationAccessor? = null

    override val isDecoroutinatorEnabled: Boolean
        get() = enabled

    override val baseContinuationAccessor: BaseContinuationAccessor?
        get() = _baseContinuationAccessor

    @Suppress("NewApi")
    override fun prepareBaseContinuationAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor =
        prepareBaseContinuationAccessorLock.withLock {
            _baseContinuationAccessor?.let { return it }
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

    override fun registerTransformedClass(lookup: MethodHandles.Lookup) {
        transformedClassesRegistry.registerTransformedClass(lookup)
    }

    override val isTailCallDeoptimizationEnabled: Boolean
        get() = tailCallDeoptimize

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

    override val isUsingElementFactoryForBaseContinuationEnabled: Boolean
        get() = dev.reformator.stacktracedecoroutinator.common.internal.isUsingElementFactoryForBaseContinuationEnabled

    override fun getElementFactoryStacktraceElement(baseContinuation: Any): StackTraceElement? =
        if (baseContinuation is ContinuationCached) {
            baseContinuation.`$decoroutinator$cache`?.element
        } else {
            stacktraceElementsFactory.getStacktraceElement(baseContinuation as BaseContinuation)
        }

    override val fillUnknownElementsWithClassName: Boolean
        get() = dev.reformator.stacktracedecoroutinator.common.internal.fillUnknownElementsWithClassName

    override val isUsingElementCacheForManualContinuationGetElementMethodEnabled: Boolean
        get() = dev.reformator.stacktracedecoroutinator.common.internal.isUsingElementCacheForManualContinuationGetElementMethodEnabled
}
