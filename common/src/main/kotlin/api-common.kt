@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorCommonApiKt")

package dev.reformator.stacktracedecoroutinator.common

import dev.reformator.stacktracedecoroutinator.common.internal.*
import dev.reformator.stacktracedecoroutinator.common.internal.ENABLED_PROPERTY
import dev.reformator.stacktracedecoroutinator.common.internal.enabled
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidKeep
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn


data class DecoroutinatorStatus(val successful: Boolean, val description: String)

object DecoroutinatorCommonApi {
    fun interface LocalSuspendCall {
        suspend operator fun invoke(callThis: suspend () -> Unit)
    }

    /**
     * Get the status of Decoroutinator correctness.
     * @param sourceCall call this lambda. Need to check status properly. See the sample below.
     * @sample getStatusSample
     */
    fun getStatus(
        allowTailCallOptimization: Boolean = false,
        sourceCall: LocalSuspendCall = LocalSuspendCall { it() }
    ): DecoroutinatorStatus {
        if (!supportsMethodHandle) {
            return DecoroutinatorStatus(
                successful = false,
                description = "The platform doesn't support MethodHandle's API"
            )
        }
        if (_methodHandleInvoker == null) {
            return DecoroutinatorStatus(
                successful = false,
                description = "Mandatory service '${MethodHandleInvoker::class.java.simpleName}' is not found"
            )
        }
        if (_baseContinuationAccessorProvider == null) {
            return DecoroutinatorStatus(
                successful = false,
                description = "Mandatory service '${BaseContinuationAccessorProvider::class.java.simpleName}' is not found"
            )
        }
        if (!enabled) {
            return DecoroutinatorStatus(
                successful = false,
                description = "Decoroutinator is forcefully disabled by setting runtime parameter [$ENABLED_PROPERTY]"
            )
        }

        @Suppress("ClassName")
        @AndroidKeep
        class _continuation: Continuation<DecoroutinatorStatus> {
            var continuation: Continuation<Unit>? = null
            var wasSuspended = false
            var status: DecoroutinatorStatus? = null

            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<DecoroutinatorStatus>) {
                require(wasSuspended)
                require(status == null)
                status = result.getOrThrow()
            }

            suspend fun suspendResumeAndGetStacktrace(): List<StackTraceElement> {
                suspendCoroutineUninterceptedOrReturn {
                    if (this.continuation != null) {
                        error("'sourceCall' must be called exactly once")
                    }
                    this.continuation = it
                    COROUTINE_SUSPENDED
                }
                return Exception().stackTrace.toList()
            }

            @Suppress("unused", "UNCHECKED_CAST")
            private suspend fun getStatus(): DecoroutinatorStatus {
                @Suppress("LocalVariableName") var _trace: List<StackTraceElement>? = null
                @Suppress("ClassName")
                @AndroidKeep
                class _lambda: suspend () -> Unit {
                    override suspend fun invoke() {
                        _trace = suspendResumeAndGetStacktrace()
                        hashCode() // tail-call deoptimize
                    }
                }
                sourceCall(_lambda())
                if (!wasSuspended) {
                    error("'sourceCall' must be called exactly once")
                }
                val trace = _trace!!
                /*
                    trace should be from top to bottom:
                    - suspendResumeAndGetStacktrace()
                    - <awaking auxiliary frames>
                    - <_lambda>
                    - <sourceCalls>
                    - getStatus()
                    - <...>
                    - AwakenerKt.awake()
                    - BaseContinuation.resumeWith()
                */
                val baseContinuationResumeIndex = trace.indexOfFirst {
                    it.className == BaseContinuation::class.java.name
                }
                if (baseContinuationResumeIndex == -1) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "Something wrong. [${BaseContinuation::class.java.name}.resumeWith()] method call is not found"
                    )
                }
                if (baseContinuationResumeIndex == 0) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "Something wrong. [${BaseContinuation::class.java.name}.resumeWith()] method is on the top"
                    )
                }
                if (trace[0].className != _continuation::class.java.name) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "Something wrong. The top call is [${trace[0]}]"
                    )
                }

                val restoredSubTrace = trace.subList(1, baseContinuationResumeIndex)

                if (restoredSubTrace.any {
                    it.className == methodHandleInvoker.unknownSpecMethodClass.name
                }) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "The stack trace contains unknown frames."
                    )
                }

                val lambdaIndex = restoredSubTrace.indexOfFirst { it.className == _lambda::class.java.name }
                if (lambdaIndex == -1) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "'_lambda' call is not found. Probably Decoroutinator wasn't installed or was obfuscated."
                    )
                }

                val getStatusMethodName = Exception().stackTrace.find {
                    it.className == _continuation::class.java.name
                }!!.methodName
                val getStatusIndex =
                    restoredSubTrace.indexOfFirst {
                        it.className == _continuation::class.java.name && it.methodName == getStatusMethodName
                    }
                if (getStatusIndex <= lambdaIndex) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "'getStatus' call is not found. Probably Decoroutinator wasn't installed or was obfuscated."
                    )
                }

                if (!allowTailCallOptimization && getStatusIndex == lambdaIndex + 1) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "The stack trace doesn't contain source call frames. Probably the source call was tail call optimized"
                    )
                }

                return DecoroutinatorStatus(
                    successful = true,
                    description = "no issues detected"
                )
            }

            fun callGetStatus() {
                javaClass.getDeclaredMethod(::getStatus.name, Continuation::class.java).invoke(this, this)
            }
        }
        val continuation = _continuation()

        continuation.callGetStatus()
        continuation.continuation.let { resumeContinuation ->
            if (resumeContinuation == null) {
                error("'sourceCall' must only call its argument, no other actions")
            }
            continuation.wasSuspended = true
            resumeContinuation.resumeWith(Result.success(Unit))
        }
        return continuation.status!!
    }
}

@Suppress("unused")
private fun getStatusSample() {
    val status = DecoroutinatorCommonApi.getStatus { it() }
    if (status.successful) {
        println("Decoroutinator is performing properly.")
    } else {
        println("Decoroutinator is not performing properly: ${status.description}.")
    }
}
