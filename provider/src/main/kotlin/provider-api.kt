@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorProviderApiKt")

package dev.reformator.stacktracedecoroutinator.provider

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.MethodNameConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidLegacyKeep
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.callInvokeSuspend
import dev.reformator.stacktracedecoroutinator.provider.internal.provider
import java.io.Serializable
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

@Suppress("unused", "PropertyName", "FunctionName")
@AndroidLegacyKeep
interface DecoroutinatorSpec {
    fun `$decoroutinator$getLineNumber`(): Int
    fun `$decoroutinator$isLastSpec`(): Boolean
    fun `$decoroutinator$getNextSpecHandle`(): MethodHandle
    fun `$decoroutinator$getNextSpec`(): DecoroutinatorSpec
    fun `$decoroutinator$resumeNext`(result: Any?): Any?
}

@Suppress("FunctionName", "PrivatePropertyName")
open class DecoroutinatorSpecImpl: DecoroutinatorSpec, Serializable {
    @Transient private var `$decoroutinator$accessor`: BaseContinuationAccessor? = null
    @Transient private var `$decoroutinator$lineNumber`: Int = 0
    @Transient private var `$decoroutinator$nextSpec`: DecoroutinatorSpec? = null
    @Transient private var `$decoroutinator$nextSpecHandle`: MethodHandle? = null
    @Transient private var `$decoroutinator$nextContinuation`: Any? = null

    fun `$decoroutinator$init`(
        accessor: BaseContinuationAccessor,
        lineNumber: Int,
        nextSpec: DecoroutinatorSpec?,
        nextSpecHandle: MethodHandle?,
        nextContinuation: Any?
    ) {
        `$decoroutinator$accessor` = accessor
        `$decoroutinator$lineNumber` = lineNumber
        `$decoroutinator$nextSpec` = nextSpec
        `$decoroutinator$nextSpecHandle` = nextSpecHandle
        `$decoroutinator$nextContinuation` = nextContinuation
    }

    final override fun `$decoroutinator$getLineNumber`(): Int {
        val result = `$decoroutinator$lineNumber`
        `$decoroutinator$lineNumber` = 0
        return result
    }

    final override fun `$decoroutinator$isLastSpec`(): Boolean =
        `$decoroutinator$nextSpec` == null

    final override fun `$decoroutinator$getNextSpecHandle`(): MethodHandle {
        val result = `$decoroutinator$nextSpecHandle`!!
        `$decoroutinator$nextSpecHandle` = null
        return result
    }

    final override fun `$decoroutinator$getNextSpec`(): DecoroutinatorSpec {
        val result = `$decoroutinator$nextSpec`!!
        `$decoroutinator$nextSpec` = null
        return result
    }

    final override fun `$decoroutinator$resumeNext`(result: Any?): Any? {
        val accessorCopy = `$decoroutinator$accessor`!!
        `$decoroutinator$accessor` = null
        val nextContinuationCopy = `$decoroutinator$nextContinuation`
        if (nextContinuationCopy != null) {
            `$decoroutinator$nextContinuation` = null
            if (result !== provider.coroutineSuspendedMarker) {
                return callInvokeSuspend(
                    baseContinuation = nextContinuationCopy,
                    accessor = accessorCopy,
                    result = result,
                    probeCoroutineResumed = provider::probeCoroutineResumed,
                    createFailure = provider::createFailure,
                    coroutineSuspendedMarker = provider.coroutineSuspendedMarker
                )
            }
        }
        return result
    }
}

@Suppress("unused")
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention
annotation class DecoroutinatorTransformed(
    @Suppress("unused")
    @get:MethodNameConstant("decoroutinatorTransformedFileNamePresentMethodName")
    @get:JvmName("fnp")
    val fileNamePresent: Boolean = true,

    @get:MethodNameConstant("decoroutinatorTransformedFileNameMethodName")
    @get:JvmName("fn")
    val fileName: String = "",

    @get:MethodNameConstant("decoroutinatorTransformedClassNameMethodName")
    @get:JvmName("cn")
    val className: String = "",

    @get:MethodNameConstant("decoroutinatorTransformedModeMethodName")
    @get:JvmName("m")
    val mode: Mode = Mode.FULL
) {
    enum class Mode {
        FULL, SKIP_SPEC_METHODS, PRESERVE_CLASS_LAYOUT
    }
}

@Suppress("unused")
@Target(AnnotationTarget.FUNCTION)
@Retention
annotation class DecoroutinatorSpecMethod(
    @get:MethodNameConstant("decoroutinatorSpecMethodMethodNameMethodName")
    @get:JvmName("mn")
    val methodName: String,

    @get:MethodNameConstant("decoroutinatorSpecMethodLineNumbersMethodName")
    @get:JvmName("ln")
    val lineNumbers: IntArray
)

interface ContinuationCached {
    @Suppress("unused", "PropertyName")
    val `$decoroutinator$cache`: SpecCache?
}

interface BaseContinuationExtractor: ContinuationCached {
    @Suppress("PropertyName")
    @get:MethodNameConstant("baseContinuationExtractorGetLabelMethodName")
    val `$decoroutinator$label`: Int

    @Suppress("PropertyName")
    @get:MethodNameConstant("baseContinuationExtractorGetCachesMethodName")
    val `$decoroutinator$caches`: Array<SpecCache>

    override val `$decoroutinator$cache`: SpecCache
        get() = `$decoroutinator$caches`[`$decoroutinator$label` and Int.MAX_VALUE]
}

interface ManualContinuation: ContinuationCached {
    @Suppress("PropertyName")
    @get:MethodNameConstant("manualContinuationGetCacheFieldMethodName")
    val `$decoroutinator$cacheField`: SpecCache

    @Suppress("PropertyName")
    @get:MethodNameConstant("manualContinuationGetClassFieldMethodName")
    val `$decoroutinator$classField`: Class<*>

    @get:MethodNameConstant("manualContinuationGetCacheMethodName")
    override val `$decoroutinator$cache`: SpecCache?
        get() = when {
            !provider.fillUnknownElementsWithClassName -> provider.nullElementSpecCache
            javaClass !== `$decoroutinator$classField` -> null
            else -> `$decoroutinator$cacheField`
        }
}

interface LazilyCachedContinuation: ContinuationCached {
    @Suppress("PropertyName")
    @get:MethodNameConstant("lazilyCachedContinuationGetCacheFieldMethodName")
    @set:MethodNameConstant("lazilyCachedContinuationSetCacheFieldMethodName")
    var `$decoroutinator$cacheField`: SpecCache?

    override val `$decoroutinator$cache`: SpecCache
        get() {
            val cacheField = `$decoroutinator$cacheField`
            return if (cacheField == null) {
                val element = provider.getCoroutineStackFrameStackTraceElement(this)
                val result = if (element == null) {
                    if (provider.fillUnknownElementsWithClassName) {
                        SpecCache(javaClass.name, "resumeWith", null, -1)
                    } else provider.nullElementSpecCache
                } else SpecCache(element)
                `$decoroutinator$cacheField` = result
                result
            } else cacheField
        }
}

@Suppress("unused")
class SpecCache(
    @get:MethodNameConstant("specCacheGetElementMethodName")
    val element: StackTraceElement?
) {
    var specMethod: MethodHandle? = null

    constructor(
        className: String,
        methodName: String,
        fileName: String?,
        lineNumber: Int
    ): this(StackTraceElement(
        className,
        methodName,
        fileName,
        lineNumber
    ))
}

@Suppress("unused")
val isDecoroutinatorEnabled: Boolean
    @MethodNameConstant("isDecoroutinatorEnabledMethodName") get() = provider.isDecoroutinatorEnabled

@Suppress("unused")
@MethodNameConstant("registerTransformedClassMethodName")
fun registerTransformedClass(lookup: MethodHandles.Lookup) {
    provider.registerTransformedClass(lookup)
}

@Suppress("unused")
val isTailCallDeoptimizationEnabled: Boolean
    @MethodNameConstant("isTailCallDeoptimizationEnabledMethodName")
    get() = provider.isTailCallDeoptimizationEnabled

@Suppress("unused")
@MethodNameConstant("tailCallDeoptimizeMethodName")
fun tailCallDeoptimize(completion: Any, cache: SpecCache?): Any =
    provider.tailCallDeoptimize(completion, cache)

@Suppress("unused")
val isUsingElementFactoryForBaseContinuationEnabled: Boolean
    @MethodNameConstant("isUsingElementFactoryForBaseContinuationEnabledMethodName")
    get() = provider.isUsingElementFactoryForBaseContinuationEnabled

@Suppress("unused")
val fillUnknownElementsWithClassName: Boolean
    @MethodNameConstant("fillUnknownElementsWithClassNameMethodName")
    get() = provider.fillUnknownElementsWithClassName

@Suppress("unused")
val isUsingElementCacheForManualContinuationGetElementMethodEnabled: Boolean
    @MethodNameConstant("isUsingElementCacheForManualContinuationGetElementMethodEnabledMethodName")
    get() = provider.isUsingElementCacheForManualContinuationGetElementMethodEnabled

@Suppress("unused")
val isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabled: Boolean
    @MethodNameConstant("isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabledMethodName")
    get() = provider.isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabled

val providerApiClass: Class<*>
    @GetOwnerClass get() { fail() }
