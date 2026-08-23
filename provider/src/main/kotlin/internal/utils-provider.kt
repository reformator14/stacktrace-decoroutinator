package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.lang.invoke.MethodHandle

val specLineNumberMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Int::class.javaPrimitiveType && it.parameterCount == 0 }!!
    .name

val isLastSpecMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Boolean::class.javaPrimitiveType && it.parameterCount == 0 }!!
    .name

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val nextSpecHandleMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == MethodHandle::class.java && it.parameterCount == 0 }!!
    .name

val nextSpecMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == DecoroutinatorSpec::class.java && it.parameterCount == 0 }!!
    .name

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val resumeNextMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Object::class.java && it.parameterCount == 1 }!!
    .name

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val String.internalName: String
    get() = (this as java.lang.String).replace('.', '/')

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val String.binaryName: String
    get() = (this as java.lang.String).replace('/', '.')

inline fun <reified BASE_CONTINUATION: Any> callInvokeSuspend(
    baseContinuation: BASE_CONTINUATION,
    accessor: BaseContinuationAccessor,
    result: Any?,
    probeCoroutineResumed: (BASE_CONTINUATION) -> Unit,
    createFailure: (Throwable) -> Any,
    coroutineSuspendedMarker: Any,
): Any? {
    probeCoroutineResumed(baseContinuation)
    val newResult = try {
        accessor.invokeSuspend(baseContinuation, result)
    } catch (exception: Throwable) {
        createFailure(exception)
    }
    if (newResult === coroutineSuspendedMarker) {
        return newResult
    }
    accessor.releaseIntercepted(baseContinuation)
    return newResult
}
