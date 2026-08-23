@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.intrinsics

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.intrinsics.MethodNameConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.CoroutineStackFrame

const val BASE_CONTINUATION_CLASS_NAME = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"
const val UNKNOWN_LINE_NUMBER = -1
const val PROVIDER_MODULE_NAME = "dev.reformator.stacktracedecoroutinator.provider"
const val LABEL_FIELD_NAME = "label"

@ChangeClassName(toName = BASE_CONTINUATION_CLASS_NAME)
abstract class BaseContinuation: Continuation<Any?>, CoroutineStackFrame {
    @Suppress("RedundantNullableReturnType", "unused")
    val completion: Continuation<Any?>?
        get() = fail()

    final override fun resumeWith(result: Result<Any?>) { fail() }

    init { fail() }

    override fun getStackTraceElement(): StackTraceElement? { fail() }

    abstract fun invokeSuspend(result: Any?): Any?

    open fun releaseIntercepted() { fail() }

    override val callerFrame: CoroutineStackFrame?
        get() = fail()
}

@ChangeClassName(toName = "kotlin.coroutines.jvm.internal.DebugMetadata")
@Target(AnnotationTarget.CLASS)
annotation class DebugMetadata(
    @get:MethodNameConstant("debugMetadataFileNameMethodName")
    @get:JvmName("f")
    @Suppress("unused")
    val fileName: String = "",

    @get:MethodNameConstant("debugMetadataLineNumbersMethodName")
    @get:JvmName("l")
    @Suppress("unused")
    val lineNumbers: IntArray = [],

    @get:MethodNameConstant("debugMetadataMethodNameMethodName")
    @get:JvmName("m")
    @Suppress("unused")
    val methodName: String = "",

    @get:MethodNameConstant("debugMetadataClassNameMethodName")
    @get:JvmName("c")
    @Suppress("unused")
    val className: String = ""
)

inline fun ifAssertionEnabled(check: () -> Unit) {
    if (_Assertions.ENABLED) {
        check()
    }
}

inline fun assert(check: () -> Boolean) {
    ifAssertionEnabled {
        if (!check()) {
            throw AssertionError()
        }
    }
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
