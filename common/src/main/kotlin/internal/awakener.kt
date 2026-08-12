@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.common.intrinsics.FailureResult
import dev.reformator.stacktracedecoroutinator.common.intrinsics.toResult
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.intrinsics.UNKNOWN_LINE_NUMBER
import dev.reformator.stacktracedecoroutinator.provider.ContinuationCached
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import java.lang.invoke.MethodHandle
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.jvm.internal.CoroutineStackFrame
import kotlin.math.max

internal fun BaseContinuation.awake(accessor: BaseContinuationAccessor, result: Any?) {
    val isRecoveryExplicitStacktraceNeeded = recoveryExplicitStacktrace && result.toResult.isFailure
    val elementsAndSpecMethods = if (isRecoveryExplicitStacktraceNeeded) getElementsAndSpecMethods() else null
    if (isRecoveryExplicitStacktraceNeeded) {
        recoveryExplicitStacktrace(
            exception = (result as FailureResult).exception,
            elementsAndSpecMethods = elementsAndSpecMethods!!
        )
    }

    if (result === COROUTINE_SUSPENDED) {
        stdlibAwake(
            accessor = accessor,
            result = result
        )
        return
    }

    callSpecMethods(
        accessor = accessor,
        elementsAndSpecMethods = elementsAndSpecMethods,
        result = result
    )
}

@Suppress("MayBeConstant", "RedundantSuppression")
private val boundaryLabel = "decoroutinator-boundary"
private const val unknown = "unknown"
private val unknownStacktraceElement =
    StackTraceElement("", "", unknown, -1)
private val boundaryStacktraceElement =
    StackTraceElement("", "", boundaryLabel, -1)

@OptIn(ExperimentalContracts::class)
private inline fun CoroutineStackFrame.getElementAndSpecMethod(
    consumer: (element: StackTraceElement?, specMethod: MethodHandle) -> Unit
) {
    contract { callsInPlace(consumer, InvocationKind.EXACTLY_ONCE) }
    val cache = (this as? ContinuationCached)?.`$decoroutinator$cache`
    if (cache != null) {
        val specMethod = cache.specMethod ?: run {
            val specMethod = cache.element
                ?.let { specMethodsFactory.getSpecMethodHandle(it) }
                ?: methodHandleInvoker.unknownSpecMethodHandle
            cache.specMethod = specMethod
            specMethod
        }
        consumer(cache.element, specMethod)
    } else {
        val element = getNormalizedStackTraceElement()
        val specMethod = element?.let { specMethodsFactory.getSpecMethodHandle(it) } ?:
            methodHandleInvoker.unknownSpecMethodHandle
        consumer(element, specMethod)
    }
}

private class ElementAndSpecMethod(
    val element: StackTraceElement?,
    val specMethod: MethodHandle
)

private fun BaseContinuation.getElementsAndSpecMethods(): List<ElementAndSpecMethod> =
    buildList {
        var frame: CoroutineStackFrame? = this@getElementsAndSpecMethods
        while (frame != null) {
            val element: StackTraceElement?
            val specMethod: MethodHandle
            frame.getElementAndSpecMethod { gotElement, gotSpecMethod ->
                element = gotElement
                specMethod = gotSpecMethod
            }
            add(ElementAndSpecMethod(element, specMethod))
            frame = frame.callerFrame
        }
    }

private fun BaseContinuation.stdlibAwake(accessor: BaseContinuationAccessor, result: Any?) {
    var newResult = result
    var baseContinuation = this
    do {
        newResult = baseContinuation.callInvokeSuspend(
            accessor = accessor,
            result = newResult
        )
        if (newResult === COROUTINE_SUSPENDED) return
        baseContinuation = baseContinuation.completion!! as? BaseContinuation ?: break
    } while (true)
    baseContinuation.completion!!.resumeWith(newResult.toResult)
}

private fun BaseContinuation.callSpecMethods(
    accessor: BaseContinuationAccessor,
    elementsAndSpecMethods: List<ElementAndSpecMethod>?,
    result: Any?
) {
    var spec: DecoroutinatorSpec? = null
    var specMethod: MethodHandle? = null
    var baseContinuation: BaseContinuation? = this //next
    var elementIndex = 1
    var frame: CoroutineStackFrame? = null //current
    var completion: Continuation<Any?> = this
    while (true) {
        val currentBaseContinuation = baseContinuation
        val element: StackTraceElement?
        val elementSpecMethod: MethodHandle

        if (elementsAndSpecMethods != null) {
            if (elementIndex == elementsAndSpecMethods.size) {
                if (currentBaseContinuation != null) {
                    completion = currentBaseContinuation.completion!!
                }
                break
            } else {
                if (currentBaseContinuation != null) {
                    completion = currentBaseContinuation.completion!!
                    baseContinuation = completion as? BaseContinuation
                }
                val elementAndSpecMethod = elementsAndSpecMethods[elementIndex++]
                element = elementAndSpecMethod.element
                elementSpecMethod = elementAndSpecMethod.specMethod
            }
        } else if (currentBaseContinuation != null) {
            val currentCompletion = currentBaseContinuation.completion!!
            if (currentCompletion is BaseContinuation) {
                baseContinuation = currentCompletion
                currentCompletion.getElementAndSpecMethod { gotElement, gotSpecMethod ->
                    element = gotElement
                    elementSpecMethod = gotSpecMethod
                }
            } else {
                completion = currentCompletion
                if (currentCompletion is CoroutineStackFrame) {
                    baseContinuation = null
                    frame = currentCompletion.callerFrame
                    currentCompletion.getElementAndSpecMethod { gotElement, gotSpecMethod ->
                        element = gotElement
                        elementSpecMethod = gotSpecMethod
                    }
                } else break
            }
        } else {
            val currentFrame = frame ?: break
            frame = currentFrame.callerFrame
            currentFrame.getElementAndSpecMethod { gotElement, gotSpecMethod ->
                element = gotElement
                elementSpecMethod = gotSpecMethod
            }
        }

        spec = DecoroutinatorSpecImpl(
            accessor = accessor,
            lineNumber =
                @Suppress("IfThenToElvis") if (element == null) {
                    UNKNOWN_LINE_NUMBER
                } else {
                    element.normalizedLineNumber
                },
            _nextSpec = spec,
            _nextSpecHandle = specMethod,
            nextContinuation = currentBaseContinuation
        )

        specMethod = elementSpecMethod
    }

    val specResult = if (spec != null) {
        val specResult = methodHandleInvoker.callSpecMethod(
            handle = specMethod!!,
            spec = spec,
            result = result
        )
        if (specResult === COROUTINE_SUSPENDED) return
        specResult
    } else {
        result
    }

    val baseContinuationResult = if (baseContinuation != null) {
        val baseContinuationResult = baseContinuation.callInvokeSuspend(accessor, specResult)
        if (baseContinuationResult === COROUTINE_SUSPENDED) return
        baseContinuationResult
    } else {
        specResult
    }

    completion.resumeWith(baseContinuationResult.toResult)
}

private fun boundaryStackTraceElement(time: UInt): StackTraceElement =
    StackTraceElement("", "", boundaryLabel, time.toInt())

private fun currentTime(): UInt =
    System.currentTimeMillis().toUInt()

private fun recoveryExplicitStacktrace(
    exception: Throwable,
    elementsAndSpecMethods: List<ElementAndSpecMethod>
) {
    val trace = exception.stackTrace
    exception.stackTrace = run {
        val boundaryIndex = trace.indexOfFirst { it === boundaryStacktraceElement }
        if (boundaryIndex == -1) {
            return@run Array(trace.size + elementsAndSpecMethods.size + 2) {
                if (it < trace.size) {
                    trace[it]
                } else if (it == trace.size) {
                    boundaryStacktraceElement
                } else {
                    val framesIndex = it - trace.size - 1
                    if (framesIndex < elementsAndSpecMethods.size) {
                        elementsAndSpecMethods[framesIndex].element ?: unknownStacktraceElement
                    } else {
                        assert { framesIndex == elementsAndSpecMethods.size }
                        boundaryStackTraceElement(currentTime())
                    }
                }
            }
        }

        val lastBoundaryIndex = max(
            trace.indexOfLast { it.className.isEmpty() && it.methodName.isEmpty() && it.fileName === boundaryLabel },
            boundaryIndex
        )
        val time = currentTime()
        val erasePreviousBoundaries = lastBoundaryIndex > boundaryIndex &&
                time > recoveryExplicitStacktraceTimeoutMs &&
                trace[lastBoundaryIndex].lineNumber.toUInt() < time - recoveryExplicitStacktraceTimeoutMs
        val prefixEndIndex = (if (erasePreviousBoundaries) boundaryIndex else lastBoundaryIndex) + 1

        Array(prefixEndIndex + elementsAndSpecMethods.size + trace.size - lastBoundaryIndex) {
            if (it < prefixEndIndex) {
                trace[it]
            } else {
                val framesIndex = it - prefixEndIndex
                if (framesIndex < elementsAndSpecMethods.size) {
                    elementsAndSpecMethods[framesIndex].element ?: unknownStacktraceElement
                } else if (framesIndex == elementsAndSpecMethods.size) {
                    boundaryStackTraceElement(time)
                } else {
                    val suffixIndex = framesIndex - elementsAndSpecMethods.size
                    trace[lastBoundaryIndex + suffixIndex]
                }
            }
        }
    }
}
