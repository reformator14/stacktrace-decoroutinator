@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.intrinsics

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.intrinsics.ChangeInvocationsOwner
import dev.reformator.bytecodeprocessor.intrinsics.SkipInvocations
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.jvm.internal.CoroutineStackFrame

@ChangeClassName(
    toName = "kotlin.Result\$Failure",
    deleteAfterChanging = true
)
internal class FailureResult {
    @JvmField val exception: Throwable = fail()
}

@ChangeClassName(
    toName = "kotlin._Assertions",
    deleteAfterChanging = true
)
@PublishedApi
@Suppress("ClassName")
internal object _Assertions {
    @JvmField
    val ENABLED: Boolean = fail()
}

@Suppress("UnusedReceiverParameter")
internal val Any?.toResult: Result<*>
    @SkipInvocations get() { fail() }

@Suppress("UNUSED_PARAMETER")
@ChangeInvocationsOwner(toName = "kotlin.coroutines.jvm.internal.DebugProbesKt")
internal fun probeCoroutineResumed(frame: Continuation<*>) { fail() }

@Suppress("UNUSED_PARAMETER")
@ChangeInvocationsOwner(toName = "kotlin.ResultKt")
internal fun createFailure(exception: Throwable): Any { fail() }

@ChangeClassName(toName = "kotlin.coroutines.jvm.internal.ContinuationImpl", deleteAfterChanging = true)
internal abstract class ContinuationImpl(
    @Suppress("UNUSED_PARAMETER") completion: Continuation<Any?>?
): BaseContinuation(), CoroutineStackFrame {
    override val context: CoroutineContext
        get() { fail() }

    override val callerFrame: CoroutineStackFrame?
        get() { fail() }

    override fun getStackTraceElement(): StackTraceElement? { fail() }
}
