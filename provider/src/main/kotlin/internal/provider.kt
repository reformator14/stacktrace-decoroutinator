@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.stacktracedecoroutinator.provider.SpecCache
import java.lang.invoke.MethodHandles
import java.util.ServiceLoader

interface DecoroutinatorProvider {
    val isDecoroutinatorEnabled: Boolean
    val baseContinuationAccessor: BaseContinuationAccessor?
    fun prepareBaseContinuationAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor
    fun awakeBaseContinuation(accessor: BaseContinuationAccessor, baseContinuation: Any, result: Any?)
    fun registerTransformedClass(lookup: MethodHandles.Lookup)
    val isTailCallDeoptimizationEnabled: Boolean
    fun tailCallDeoptimize(completion: Any, cache: SpecCache?): Any
    val isUsingElementFactoryForBaseContinuationEnabled: Boolean
    fun getElementFactoryStacktraceElement(baseContinuation: Any): StackTraceElement?
    val fillUnknownElementsWithClassName: Boolean
    val isUsingElementCacheForManualContinuationGetElementMethodEnabled: Boolean
    fun getCoroutineStackFrameStackTraceElement(coroutineStackFrame: Any): StackTraceElement?
    val nullElementSpecCache: SpecCache
    val isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabled: Boolean
    val coroutineSuspendedMarker: Any
    fun probeCoroutineResumed(frameContinuation: Any)
    fun createFailure(exception: Throwable): Any
}

internal val provider: DecoroutinatorProvider =
    try {
        ServiceLoader.load(DecoroutinatorProvider::class.java).iterator().next()
    } catch (_: Throwable) {
        null
    } ?: NoopProvider()

private class NoopProvider: DecoroutinatorProvider {
    override val isDecoroutinatorEnabled: Boolean
        get() = false

    override val baseContinuationAccessor: BaseContinuationAccessor
        get() = error("not supported")

    override fun prepareBaseContinuationAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor =
        error("not supported")

    override fun awakeBaseContinuation(
        accessor: BaseContinuationAccessor,
        baseContinuation: Any,
        result: Any?
    ) {
        error("not supported")
    }

    override fun registerTransformedClass(lookup: MethodHandles.Lookup) {
        error("not supported")
    }

    override val isTailCallDeoptimizationEnabled: Boolean
        get() = false

    override fun tailCallDeoptimize(completion: Any, cache: SpecCache?): Any =
        completion

    override val isUsingElementFactoryForBaseContinuationEnabled: Boolean
        get() = false

    override fun getElementFactoryStacktraceElement(baseContinuation: Any): StackTraceElement =
        error("not supported")

    override val fillUnknownElementsWithClassName: Boolean
        get() = false

    override val isUsingElementCacheForManualContinuationGetElementMethodEnabled: Boolean
        get() = false

    override fun getCoroutineStackFrameStackTraceElement(coroutineStackFrame: Any): StackTraceElement =
        error("not supported")

    override val nullElementSpecCache: SpecCache
        get() = error("not supported")

    override val isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabled: Boolean
        get() = false

    override val coroutineSuspendedMarker: Any
        get() = error("not supported")

    override fun probeCoroutineResumed(frameContinuation: Any) {
        error("not supported")
    }

    override fun createFailure(exception: Throwable): Any {
        error("not supported")
    }
}

interface BaseContinuationAccessor {
    fun invokeSuspend(baseContinuation: Any, result: Any?): Any?
    fun releaseIntercepted(baseContinuation: Any)
}

fun interface BaseContinuationAccessorProvider {
    fun createAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class AndroidKeep

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class AndroidLegacyKeep
