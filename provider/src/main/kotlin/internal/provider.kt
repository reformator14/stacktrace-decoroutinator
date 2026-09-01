@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.stacktracedecoroutinator.provider.SpecCache
import java.lang.invoke.MethodHandles
import java.util.ServiceLoader

interface DecoroutinatorProvider {
    fun awakeBaseContinuation(accessor: BaseContinuationAccessor, baseContinuation: Any, result: Any?)

    fun tailCallDeoptimize(completion: Any, cache: SpecCache?): Any

    fun getElementFactoryStacktraceElement(baseContinuation: Any): StackTraceElement?

    fun getCoroutineStackFrameStackTraceElement(coroutineStackFrame: Any): StackTraceElement?

    fun callInvokeSuspendIfResultIsNotCoroutineSuspended(
        baseContinuation: Any,
        accessor: BaseContinuationAccessor,
        result: Any?
    ): Any?

    // baseContinuation scopes the cached accessor per class loader (see DispatchingProvider) -
    // a single process-wide cache breaks once more than one class loader defines a class named
    // BaseContinuationImpl in the same process (e.g. a reloaded plugin module): the cached
    // accessor's MethodHandles stay bound to whichever BaseContinuationImpl they were first built
    // against, and invoking them against an instance of a different (even identically-named,
    // identically-versioned) BaseContinuationImpl throws ClassCastException.
    fun getBaseContinuationAccessor(baseContinuation: Any): BaseContinuationAccessor?

    // No baseContinuation parameter here (unlike getBaseContinuationAccessor above): this is only
    // ever called with a lookup obtained from MethodHandles.lookup() invoked inside
    // BaseContinuationImpl's own resumeWith, so lookup.lookupClass() is always that same
    // BaseContinuationImpl - already exactly the class loader identity needed to scope this the
    // same way as getBaseContinuationAccessor.
    fun prepareBaseContinuationAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor
}

internal val provider: DecoroutinatorProvider =
    try {
        ServiceLoader.load(DecoroutinatorProvider::class.java).iterator().next()
    } catch (_: Throwable) {
        null
    } ?: NoopProvider()

private class NoopProvider: DecoroutinatorProvider {
    override fun awakeBaseContinuation(
        accessor: BaseContinuationAccessor,
        baseContinuation: Any,
        result: Any?
    ) {
        error("not supported")
    }

    override fun tailCallDeoptimize(completion: Any, cache: SpecCache?): Any =
        completion

    override fun getElementFactoryStacktraceElement(baseContinuation: Any): StackTraceElement =
        error("not supported")

    override fun getCoroutineStackFrameStackTraceElement(coroutineStackFrame: Any): StackTraceElement =
        error("not supported")

    override fun callInvokeSuspendIfResultIsNotCoroutineSuspended(
        baseContinuation: Any,
        accessor: BaseContinuationAccessor,
        result: Any?
    ): Any =
        error("not supported")

    override fun getBaseContinuationAccessor(baseContinuation: Any): BaseContinuationAccessor =
        error("not supported")

    override fun prepareBaseContinuationAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor =
        error("not supported")
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
