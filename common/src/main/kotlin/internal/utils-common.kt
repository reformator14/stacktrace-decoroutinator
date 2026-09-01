@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.common.intrinsics.ContinuationImpl
import dev.reformator.stacktracedecoroutinator.common.intrinsics.createFailure
import dev.reformator.stacktracedecoroutinator.common.intrinsics.probeCoroutineResumed
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.ContinuationCached
import dev.reformator.stacktracedecoroutinator.provider.SpecCache
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

internal class TailCallDeoptimizedContinuation(
    completion: Continuation<Any?>,
    override val `$decoroutinator$cache`: SpecCache
): ContinuationImpl(completion), ContinuationCached {
    override fun invokeSuspend(result: Any?): Any? =
        result

    override fun getStackTraceElement(): StackTraceElement? =
        `$decoroutinator$cache`.element
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun BaseContinuation.callInvokeSuspend(accessor: BaseContinuationAccessor, result: Any?): Any? {
    probeCoroutineResumed(this)
    val newResult = try {
        accessor.invokeSuspend(this, result)
    } catch (exception: Throwable) {
        createFailure(exception)
    }
    if (newResult === COROUTINE_SUSPENDED) {
        return newResult
    }
    accessor.releaseIntercepted(this)
    return newResult
}
