@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.common.intrinsics.FailureResult
import dev.reformator.stacktracedecoroutinator.common.intrinsics.toResult
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.intrinsics.UNKNOWN_LINE_NUMBER
import dev.reformator.stacktracedecoroutinator.intrinsics.assert
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpecImpl
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.methodHandleInvoker
import dev.reformator.stacktracedecoroutinator.provider.internal.specMethodsFactory
import java.lang.invoke.MethodHandle
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.jvm.internal.CoroutineStackFrame
import kotlin.math.max

internal fun BaseContinuation.awake(accessor: BaseContinuationAccessor, result: Any?) {
    val spec: DecoroutinatorSpec?
    val specMethod: MethodHandle?
    val baseContinuation: BaseContinuation?
    val completion: Continuation<Any?>
    if (recoveryExplicitStacktrace && result.toResult.isFailure) {
        val stackTraceElements = buildList {
            add(getStacktraceElement())
            buildSpecInfo(
                accessor = accessor,
                stackTraceElementConsumer = { add(it) },
                specInfoConsumer = { gotSpec, gotSpecMethod, gotBaseContinuation, gotCompletion ->
                    spec = gotSpec
                    specMethod = gotSpecMethod
                    baseContinuation = gotBaseContinuation
                    completion = gotCompletion
                }
            )
        }
        recoveryExplicitStacktrace(
            exception = (result as FailureResult).exception,
            elements = stackTraceElements
        )
    } else {
        buildSpecInfo(
            accessor = accessor,
            stackTraceElementConsumer = { },
            specInfoConsumer = { gotSpec, gotSpecMethod, gotBaseContinuation, gotCompletion ->
                spec = gotSpec
                specMethod = gotSpecMethod
                baseContinuation = gotBaseContinuation
                completion = gotCompletion
            }
        )
    }

    if (result === COROUTINE_SUSPENDED) {
        stdlibAwake(
            accessor = accessor,
            result = result
        )
        return
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

@Suppress("MayBeConstant", "RedundantSuppression")
private val boundaryLabel = "decoroutinator-boundary"
private const val unknown = "unknown"
private val unknownStacktraceElement =
    StackTraceElement("", "", unknown, -1)
private val boundaryStacktraceElement =
    StackTraceElement("", "", boundaryLabel, -1)

private fun StackTraceElement?.calculateSpecMethod(): MethodHandle =
    this?.let { specMethodsFactory.getSpecMethodHandle(it) } ?: methodHandleInvoker.unknownSpecMethodHandle

@OptIn(ExperimentalContracts::class)
private inline fun CoroutineStackFrame.getElementAndSpecMethod(
    consumer: (element: StackTraceElement?, specMethod: MethodHandle) -> Unit
) {
    contract { callsInPlace(consumer, InvocationKind.EXACTLY_ONCE) }
    getOptionalSpecCacheAndStacktraceElement { specCache, element ->
        val specMethod = if (specCache != null) {
            specCache.specMethod ?: run {
                val calculatedSpecMethod = element.calculateSpecMethod()
                specCache.specMethod = calculatedSpecMethod
                calculatedSpecMethod
            }
        } else {
            element.calculateSpecMethod()
        }
        consumer(element, specMethod)
    }
}

private fun CoroutineStackFrame.getStacktraceElement(): StackTraceElement? {
    getOptionalSpecCacheAndStacktraceElement { _, element -> return element }
}

private fun BaseContinuation.stdlibAwake(accessor: BaseContinuationAccessor, result: Any?) {
    var newResult = result
    var baseContinuation = this
    do {
        newResult = baseContinuation.callInvokeSuspend(accessor, newResult)
        if (newResult === COROUTINE_SUSPENDED) return
        baseContinuation = baseContinuation.completion!! as? BaseContinuation ?: break
    } while (true)
    baseContinuation.completion!!.resumeWith(newResult.toResult)
}

@OptIn(ExperimentalContracts::class)
private inline fun BaseContinuation.buildSpecInfo(
    accessor: BaseContinuationAccessor,
    crossinline stackTraceElementConsumer: (StackTraceElement?) -> Unit,
    specInfoConsumer: (
        spec: DecoroutinatorSpec?,
        specMethod: MethodHandle?,
        baseContinuation: BaseContinuation?,
        completion: Continuation<Any?>
    ) -> Unit
) {
    contract { callsInPlace(specInfoConsumer, InvocationKind.EXACTLY_ONCE) }

    var spec: DecoroutinatorSpec? = null // chain built so far; head = most recently (outermost) built spec
    var specMethod: MethodHandle? = null // spec method matching `spec`'s element; becomes the next spec's nextSpecHandle
    var baseContinuation: BaseContinuation? = this
    var frame: CoroutineStackFrame? = null
    var completion: Continuation<Any?>? = null // set once, when we leave the BaseContinuation chain; asserted non-null below
    // suppression is valid because BaseContinuationImpl can be reparented onto DecoroutinatorSpecImpl by class-transformer
    @Suppress("CAST_NEVER_SUCCEEDS") var specHolder: DecoroutinatorSpecImpl? = this as? DecoroutinatorSpecImpl

    while (true) {
        val currentElement: StackTraceElement?
        val currentSpecMethod: MethodHandle
        val currentBaseContinuation: BaseContinuation?
        val currentFrame: CoroutineStackFrame?
        val currentSpecHolder: DecoroutinatorSpecImpl?

        val baseContinuationCopy = baseContinuation
        if (baseContinuationCopy != null) {
            val completionCopy = baseContinuationCopy.completion!!
            if (completionCopy is BaseContinuation) {
                completionCopy.getElementAndSpecMethod { gotElement, gotSpecMethod ->
                    currentElement = gotElement
                    currentSpecMethod = gotSpecMethod
                }
                currentBaseContinuation = completionCopy
                currentFrame = null
            } else {
                completion = completionCopy
                if (completionCopy is CoroutineStackFrame) {
                    completionCopy.getElementAndSpecMethod { gotElement, gotSpecMethod ->
                        currentElement = gotElement
                        currentSpecMethod = gotSpecMethod
                    }
                    currentBaseContinuation = null
                    currentFrame = completionCopy.callerFrame
                } else break
            }
            currentSpecHolder = completionCopy as? DecoroutinatorSpecImpl
        } else {
            val frameCopy = frame
            if (frameCopy != null) {
                frameCopy.getElementAndSpecMethod { gotElement, gotSpecMethod ->
                    currentElement = gotElement
                    currentSpecMethod = gotSpecMethod
                }
                currentBaseContinuation = null
                currentFrame = frameCopy.callerFrame
                currentSpecHolder = frameCopy as? DecoroutinatorSpecImpl
            } else break
        }

        stackTraceElementConsumer(currentElement)

        // currentSpecHolder is always the object getElementAndSpecMethod was just called on, above -
        // a fresh object every iteration, so it backs at most one spec below (immediately if specHolder
        // was null, one iteration later otherwise) and is never double-used. It need NOT equal
        // currentBaseContinuation/currentFrame (see CLAUDE.md's `buildSpecInfo`'s `specHolder` entry).
        @Suppress("KotlinConstantConditions")
        spec = specHolder.let { specHolderCopy ->
            if (specHolderCopy != null) {
                specHolder = currentSpecHolder
                specHolderCopy
            } else {
                currentSpecHolder ?: DecoroutinatorSpecImpl()
            }
        }.apply {
            @Suppress("IfThenToElvis")
            `$decoroutinator$init`(
                accessor = accessor,
                lineNumber = if (currentElement == null) UNKNOWN_LINE_NUMBER else currentElement.lineNumber,
                nextSpec = spec,
                nextSpecHandle = specMethod,
                nextContinuation = baseContinuation
            )
        }
        specMethod = currentSpecMethod
        baseContinuation = currentBaseContinuation
        frame = currentFrame
    }

    specInfoConsumer(spec, specMethod, baseContinuation, completion!!)
}

private fun boundaryStackTraceElement(time: UInt): StackTraceElement =
    StackTraceElement("", "", boundaryLabel, time.toInt())

private fun currentTime(): UInt =
    System.currentTimeMillis().toUInt()

private fun recoveryExplicitStacktrace(exception: Throwable, elements: List<StackTraceElement?>) {
    val trace = exception.stackTrace
    exception.stackTrace = run {
        val boundaryIndex = trace.indexOfFirst { it === boundaryStacktraceElement }
        if (boundaryIndex == -1) {
            return@run Array(trace.size + elements.size + 2) {
                if (it < trace.size) {
                    trace[it]
                } else if (it == trace.size) {
                    boundaryStacktraceElement
                } else {
                    val framesIndex = it - trace.size - 1
                    if (framesIndex < elements.size) {
                        elements[framesIndex] ?: unknownStacktraceElement
                    } else {
                        assert { framesIndex == elements.size }
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

        Array(prefixEndIndex + elements.size + trace.size - lastBoundaryIndex) {
            if (it < prefixEndIndex) {
                trace[it]
            } else {
                val framesIndex = it - prefixEndIndex
                if (framesIndex < elements.size) {
                    elements[framesIndex] ?: unknownStacktraceElement
                } else if (framesIndex == elements.size) {
                    boundaryStackTraceElement(time)
                } else {
                    val suffixIndex = framesIndex - elements.size
                    trace[lastBoundaryIndex + suffixIndex]
                }
            }
        }
    }
}
