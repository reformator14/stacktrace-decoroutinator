@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorProviderApiKt")

package dev.reformator.stacktracedecoroutinator.provider

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.MethodNameConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidLegacyKeep
import dev.reformator.stacktracedecoroutinator.provider.internal.provider
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

@Suppress("unused")
@AndroidLegacyKeep
interface DecoroutinatorSpec {
    val lineNumber: Int
    val isLastSpec: Boolean
    val nextSpecHandle: MethodHandle
    val nextSpec: DecoroutinatorSpec
    fun resumeNext(result: Any?): Any?
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

    @Suppress("unused")
    @get:MethodNameConstant("decoroutinatorTransformedMethodNamesMethodName")
    @get:JvmName("mn")
    val methodNames: Array<String> = [],

    @Suppress("unused")
    @get:MethodNameConstant("decoroutinatorTransformedLineNumbersCountsMethodName")
    @get:JvmName("lnc")
    val lineNumbersCounts: IntArray = [],

    @get:MethodNameConstant("decoroutinatorTransformedLineNumbersMethodName")
    @get:JvmName("ln")
    val lineNumbers: IntArray = [],

    @get:MethodNameConstant("decoroutinatorTransformedSkipSpecMethodsMethodName")
    @get:JvmName("ssm")
    val skipSpecMethods: Boolean = false
)

interface ContinuationCached {
    @Suppress("unused", "PropertyName")
    val `$decoroutinator$cache`: SpecCache?

    @Suppress("unused", "PropertyName")
    @get:MethodNameConstant("continuationCachedGetCacheElementMethodName")
    val `$decoroutinator$cacheElement`: StackTraceElement?
        get() = `$decoroutinator$cache`?.element
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

    override val `$decoroutinator$cache`: SpecCache?
        get() {
            val cache = `$decoroutinator$cacheField`
            return if (javaClass.name == cache.element.className) cache else null
        }
}

@Suppress("unused")
class SpecCache(
    val element: StackTraceElement
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

val providerApiClass: Class<*>
    @GetOwnerClass get() { fail() }
