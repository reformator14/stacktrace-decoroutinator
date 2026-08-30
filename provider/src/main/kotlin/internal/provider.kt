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
