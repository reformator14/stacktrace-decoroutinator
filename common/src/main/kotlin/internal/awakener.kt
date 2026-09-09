@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.common.intrinsics.FailureResult
import dev.reformator.stacktracedecoroutinator.common.intrinsics.toResult
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.intrinsics.assert
import dev.reformator.stacktracedecoroutinator.provider.ChecksumFailedMarker
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.HasIntIdentity
import dev.reformator.stacktracedecoroutinator.provider.SharedSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.CHECKSUM_VALID
import dev.reformator.stacktracedecoroutinator.provider.internal.DEPTH_VALID
import dev.reformator.stacktracedecoroutinator.provider.internal.doVerifySharedSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.intIdentity
import dev.reformator.stacktracedecoroutinator.provider.internal.methodHandleInvoker
import dev.reformator.stacktracedecoroutinator.provider.internal.plusKey
import dev.reformator.stacktracedecoroutinator.provider.internal.specChainBaseContinuationVerificationMode
import dev.reformator.stacktracedecoroutinator.provider.internal.specMethodsFactory
import dev.reformator.stacktracedecoroutinator.runtimesettings.SpecChainBaseContinuationVerificationMode
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
    val resumeChecksum: Int
    val depthChecksum: Int
    if (recoveryExplicitStacktrace && result.toResult.isFailure) {
        val stackTraceElements = buildList {
            add(getStacktraceElement())
            buildFirstRoundSpecChain(
                accessor = accessor,
                stackTraceElementConsumer = { add(it) },
                consumer = { gotSpec, gotSpecMethod, gotBaseContinuation, gotCompletion, gotResumeChecksum, gotDepthChecksum ->
                    spec = gotSpec
                    specMethod = gotSpecMethod
                    baseContinuation = gotBaseContinuation
                    completion = gotCompletion
                    resumeChecksum = gotResumeChecksum
                    depthChecksum = gotDepthChecksum
                }
            )
        }
        recoveryExplicitStacktrace(
            exception = (result as FailureResult).exception,
            elements = stackTraceElements
        )
    } else {
        buildFirstRoundSpecChain(
            accessor = accessor,
            stackTraceElementConsumer = { },
            consumer = { gotSpec, gotSpecMethod, gotBaseContinuation, gotCompletion, gotResumeChecksum, gotDepthChecksum ->
                spec = gotSpec
                specMethod = gotSpecMethod
                baseContinuation = gotBaseContinuation
                completion = gotCompletion
                resumeChecksum = gotResumeChecksum
                depthChecksum = gotDepthChecksum
            }
        )
    }

    if (result === COROUTINE_SUSPENDED || result === ChecksumFailedMarker) {
        stdlibAwake(
            accessor = accessor,
            result = result
        )
        return
    }

    val specChainResult = if (spec != null) {
        val firstRoundSpecChainResult = methodHandleInvoker.callSpecMethod(
            handle = specMethod!!,
            spec = spec,
            result = result,
            resumeChecksum = resumeChecksum,
            depthChecksum = depthChecksum
        )

        if (firstRoundSpecChainResult === ChecksumFailedMarker) {
            performSecondRoundSpecChain(accessor, result)
            return
        }

        if (firstRoundSpecChainResult === COROUTINE_SUSPENDED) return
        firstRoundSpecChainResult
    } else {
        result
    }

    performPostSpecChainAwaking(accessor, baseContinuation, completion, specChainResult)
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

private fun performPostSpecChainAwaking(
    accessor: BaseContinuationAccessor,
    baseContinuation: BaseContinuation?,
    completion: Continuation<Any?>,
    specChainResult: Any?
) {
    val baseContinuationResult = if (baseContinuation != null) {
        val baseContinuationResult = baseContinuation.callInvokeSuspend(accessor, specChainResult)
        if (baseContinuationResult === COROUTINE_SUSPENDED) return
        baseContinuationResult
    } else {
        specChainResult
    }
    completion.resumeWith(baseContinuationResult.toResult)
}

private fun BaseContinuation.performSecondRoundSpecChain(accessor: BaseContinuationAccessor, result: Any?) {
    buildExclusiveSpecChain(
        accessor = accessor,
        stackTraceElementConsumer = { },
        consumer = { spec, specMethod, baseContinuation, completion, depthChecksum ->
            val specChainResult = if (spec != null) {
                val specChainResult = methodHandleInvoker.callSpecMethod(
                    handle = specMethod!!,
                    spec = spec,
                    result = result,
                    resumeChecksum = CHECKSUM_VALID,
                    depthChecksum = depthChecksum
                )
                if (specChainResult === COROUTINE_SUSPENDED) return
                specChainResult
            } else {
                result
            }

            performPostSpecChainAwaking(
                accessor = accessor,
                baseContinuation = baseContinuation,
                completion = completion,
                specChainResult = specChainResult
            )
        }
    )
}

@OptIn(ExperimentalContracts::class)
private inline fun BaseContinuation.buildFirstRoundSpecChain(
    accessor: BaseContinuationAccessor,
    stackTraceElementConsumer: (StackTraceElement?) -> Unit,
    consumer: (
        spec: DecoroutinatorSpec?,
        specMethod: MethodHandle?,
        baseContinuation: BaseContinuation?,
        completion: Continuation<Any?>,
        resumeChecksum: Int,
        depthChecksum: Int
    ) -> Unit
) {
    contract { callsInPlace(consumer, InvocationKind.EXACTLY_ONCE) }
    when (specChainBaseContinuationVerificationMode) {
        SpecChainBaseContinuationVerificationMode.SHARED_NO_VERIFY,
        SpecChainBaseContinuationVerificationMode.SHARED_VERIFY -> buildSharedSpecChain(
            accessor = accessor,
            stackTraceElementConsumer = stackTraceElementConsumer,
            consumer = consumer
        )

        SpecChainBaseContinuationVerificationMode.EXCLUSIVE -> buildExclusiveSpecChain(
            accessor = accessor,
            stackTraceElementConsumer = stackTraceElementConsumer,
            consumer = { spec, specMethod, baseContinuation, completion, depthChecksum ->
                consumer(
                    spec,
                    specMethod,
                    baseContinuation,
                    completion,
                    CHECKSUM_VALID,
                    depthChecksum
                )
            }
        )
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun BaseContinuation.buildExclusiveSpecChain(
    accessor: BaseContinuationAccessor,
    stackTraceElementConsumer: (StackTraceElement?) -> Unit,
    consumer: (
        spec: DecoroutinatorSpec?,
        specMethod: MethodHandle?,
        baseContinuation: BaseContinuation?,
        completion: Continuation<Any?>,
        depthChecksum: Int
    ) -> Unit
) {
    contract { callsInPlace(consumer, InvocationKind.EXACTLY_ONCE) }
    buildSpecChain(
        stackTraceElementConsumer = stackTraceElementConsumer,
        isSharedSpecAllowed = false,
        buildSpec = { lineNumber, nextSpec, nextSpecHandle, nextContinuation, _ ->
            UncheckedExclusiveSpec(
                accessor = accessor,
                lineNumber = lineNumber,
                nextSpec = nextSpec,
                nextSpecHandle = nextSpecHandle,
                nextContinuation = nextContinuation,
            )
        },
        consumer = consumer
    )
}

@OptIn(ExperimentalContracts::class)
private inline fun BaseContinuation.buildSharedSpecChain(
    accessor: BaseContinuationAccessor,
    stackTraceElementConsumer: (StackTraceElement?) -> Unit,
    consumer: (
        spec: DecoroutinatorSpec?,
        specMethod: MethodHandle?,
        baseContinuation: BaseContinuation?,
        completion: Continuation<Any?>,
        resumeChecksum: Int,
        depthChecksum: Int
    ) -> Unit
) {
    contract { callsInPlace(consumer, InvocationKind.EXACTLY_ONCE) }
    var resumeChecksum = CHECKSUM_VALID
    buildSpecChain(
        stackTraceElementConsumer = stackTraceElementConsumer,
        isSharedSpecAllowed = true,
        buildSpec = buildSpec@{ lineNumber, nextSpec, nextSpecHandle, nextContinuation, sharedSpecToFill ->
            if (sharedSpecToFill != null) {
                val reuseSharedSpec = !doVerifySharedSpec || allowIdentityHashCodeAsIntIdentity ||
                        nextContinuation == null || nextContinuation is HasIntIdentity
                if (reuseSharedSpec) {
                    sharedSpecToFill.`$decoroutinator$init`(
                        accessor = accessor,
                        lineNumber = lineNumber,
                        nextSpec = nextSpec,
                        nextSpecHandle =  nextSpecHandle,
                        nextContinuation = nextContinuation
                    )
                    if (doVerifySharedSpec && nextContinuation != null) {
                        resumeChecksum = resumeChecksum plusKey nextContinuation.intIdentity
                    }
                    return@buildSpec sharedSpecToFill
                }
            }

            if (doVerifySharedSpec && nextContinuation != null) {
                val result = CheckedExclusiveSpec(
                    accessor = accessor,
                    lineNumber = lineNumber,
                    nextSpec = nextSpec,
                    nextSpecHandle = nextSpecHandle,
                    nextContinuation = nextContinuation
                )
                resumeChecksum = resumeChecksum plusKey result.resumeChecksumKey
                return@buildSpec result
            }

            UncheckedExclusiveSpec(
                accessor = accessor,
                lineNumber = lineNumber,
                nextSpec = nextSpec,
                nextSpecHandle = nextSpecHandle,
                nextContinuation = nextContinuation
            )
        },
        consumer = { spec, specMethod, specResult, completion, depthChecksum ->
            consumer(
                spec,
                specMethod,
                specResult,
                completion,
                resumeChecksum,
                depthChecksum
            )
        }
    )
}

@OptIn(ExperimentalContracts::class)
private inline fun BaseContinuation.buildSpecChain(
    stackTraceElementConsumer: (StackTraceElement?) -> Unit,
    isSharedSpecAllowed: Boolean,
    buildSpec: (
        lineNumber: Int,
        nextSpec: DecoroutinatorSpec?,
        nextSpecHandle: MethodHandle?,
        nextContinuation: BaseContinuation?,
        sharedSpecToFill: SharedSpec?,
    ) -> DecoroutinatorSpec,
    consumer: (
        spec: DecoroutinatorSpec?,
        specHandle: MethodHandle?,
        baseContinuation: BaseContinuation?,
        completion: Continuation<Any?>,
        depthChecksum: Int
    ) -> Unit
) {
    contract { callsInPlace(consumer, InvocationKind.EXACTLY_ONCE) }

    var spec: DecoroutinatorSpec? = null // chain built so far; head = most recently (outermost) built spec
    var specHandle: MethodHandle? = null // spec method matching `spec`'s element; becomes the next spec's nextSpecHandle
    var baseContinuation: BaseContinuation? = this
    var frame: CoroutineStackFrame? = null
    var completion: Continuation<Any?>? = null // set once, when we leave the BaseContinuation chain; asserted non-null below
    // suppression is valid because BaseContinuationImpl can be reparented onto SharedSpec by class-transformer
    @Suppress("CAST_NEVER_SUCCEEDS")
    var sharedSpec = if (isSharedSpecAllowed) this as? SharedSpec else null
    var depthChecksum = DEPTH_VALID

    while (true) {
        val currentElement: StackTraceElement?
        val currentSpecHandle: MethodHandle
        val currentBaseContinuation: BaseContinuation?
        val currentFrame: CoroutineStackFrame?
        val currentSharedSpec: SharedSpec?

        val baseContinuationCopy = baseContinuation
        if (baseContinuationCopy != null) {
            val completionCopy = baseContinuationCopy.completion!!
            if (completionCopy is BaseContinuation) {
                completionCopy.getElementAndSpecMethod { gotElement, gotSpecMethod ->
                    currentElement = gotElement
                    currentSpecHandle = gotSpecMethod
                }
                currentBaseContinuation = completionCopy
                currentFrame = null
            } else {
                completion = completionCopy
                if (completionCopy is CoroutineStackFrame) {
                    completionCopy.getElementAndSpecMethod { gotElement, gotSpecMethod ->
                        currentElement = gotElement
                        currentSpecHandle = gotSpecMethod
                    }
                    currentBaseContinuation = null
                    currentFrame = completionCopy.callerFrame
                } else break
            }
            currentSharedSpec = if (isSharedSpecAllowed) completionCopy as? SharedSpec else null
        } else {
            val frameCopy = frame
            if (frameCopy != null) {
                frameCopy.getElementAndSpecMethod { gotElement, gotSpecMethod ->
                    currentElement = gotElement
                    currentSpecHandle = gotSpecMethod
                }
                currentBaseContinuation = null
                currentFrame = frameCopy.callerFrame
                currentSharedSpec = if (isSharedSpecAllowed) frameCopy as? SharedSpec else null
            } else break
        }

        stackTraceElementConsumer(currentElement)

        // currentSharedSpec is always the object getElementAndSpecMethod was just called on, above -
        // a fresh object every iteration, so it backs at most one spec below (immediately if sharedSpec
        // was null, one iteration later otherwise) and is never double-used. It need NOT equal
        // currentBaseContinuation/currentFrame (see CLAUDE.md's `buildSpecChain`'s `sharedSpec` entry).
        spec = buildSpec(
            currentElement.normalizedLineNumber,
            spec,
            specHandle,
            baseContinuation,
            if (isSharedSpecAllowed) sharedSpec ?: currentSharedSpec else null
        )
        // buildSpec (see buildSharedSpecChain) may DECLINE the holder it was offered above - when
        // resumeChecksum verification wants a real nextContinuation identity but can't get one, it builds
        // a fresh Checked/UncheckedExclusiveSpec instead and returns that, leaving the offered holder
        // untouched. So the offered object is only actually "spent" (must not be offered again) when
        // `spec` IS that object; a decline means whatever was offered is still pristine and should stay
        // available for future iterations. Three cases:
        //  - spec === sharedSpec: the carried-over holder was consumed -> advance the carry-over to this
        //    iteration's own object, which is now the freshest unconsumed candidate.
        //  - sharedSpec == null && spec !== currentSharedSpec: no carry-over existed, currentSharedSpec was
        //    offered instead (see the `?:` above) but declined -> it's still fresh, so start carrying it
        //    forward rather than losing it.
        //  - anything else (declined while a carry-over still existed, or nothing was offered at all):
        //    leave `sharedSpec` exactly as it was - it either still holds an unconsumed candidate worth
        //    re-offering, or there was never one to begin with.
        if (isSharedSpecAllowed) {
            if (spec === sharedSpec || (sharedSpec == null && spec !== currentSharedSpec)) {
                sharedSpec = currentSharedSpec
            }
        }

        specHandle = currentSpecHandle
        baseContinuation = currentBaseContinuation
        frame = currentFrame
        depthChecksum++
    }

    consumer(
        spec,
        specHandle,
        baseContinuation,
        completion!!,
        depthChecksum
    )
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
