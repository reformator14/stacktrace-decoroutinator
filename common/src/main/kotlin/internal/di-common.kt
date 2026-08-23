package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.assert
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidKeep
import dev.reformator.stacktracedecoroutinator.runtimesettings.internal.getRuntimeSettingsValue
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal val supportsMethodHandle = supportsMethodHandle()

@Suppress("ObjectPropertyName")
internal val _methodHandleInvoker = if (supportsMethodHandle) loadService<MethodHandleInvoker>() else null

@Suppress("ObjectPropertyName")
internal val _baseContinuationAccessorProvider =
    if (_methodHandleInvoker != null) loadService<BaseContinuationAccessorProvider>() else null

internal val enabled =
    _baseContinuationAccessorProvider != null && getRuntimeSettingsValue({ enabled }) {
        System.getProperty(ENABLED_PROPERTY, "true").toBoolean()
    }

internal val recoveryExplicitStacktrace =
    enabled && getRuntimeSettingsValue({ recoveryExplicitStacktrace }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace",
            "true"
        ).toBoolean()
    }

internal val tailCallDeoptimize =
    enabled && getRuntimeSettingsValue({ tailCallDeoptimize }) {
        System.getProperty("dev.reformator.stacktracedecoroutinator.tailCallDeoptimize", "true").toBoolean()
    }

internal val recoveryExplicitStacktraceTimeoutMs =
    if (tailCallDeoptimize) {
        getRuntimeSettingsValue({ recoveryExplicitStacktraceTimeoutMs }) {
            System.getProperty(
                "dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktraceTimeoutMs",
                "500"
            ).toUInt()
        }
    } else 0U

internal val methodsNumberThreshold =
    if (enabled) {
        getRuntimeSettingsValue({ methodsNumberThreshold }) {
            System.getProperty(
                "dev.reformator.stacktracedecoroutinator.methodsNumberThreshold",
                "50"
            ).toInt()
        }
    } else 0

internal val fillUnknownElementsWithClassName =
    enabled && getRuntimeSettingsValue({ fillUnknownElementsWithClassName }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.fillUnknownElementsWithClassName",
            "true"
        ).toBoolean()
    }

internal val isUsingElementCacheForManualContinuationGetElementMethodEnabled =
    fillUnknownElementsWithClassName &&
    getRuntimeSettingsValue({ isUsingElementCacheForManualContinuationGetElementMethodEnabled }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isUsingElementCacheForManualContinuationGetElementMethodEnabled",
            "true"
        ).toBoolean()
    }

internal val isUsingElementFactoryForBaseContinuationEnabled: Boolean =
    enabled && getRuntimeSettingsValue({ isUsingElementFactoryForBaseContinuationEnabled }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isUsingElementFactoryForBaseContinuationEnabled",
            "true"
        ).toBoolean()
    }

internal val isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabled =
    fillUnknownElementsWithClassName &&
    getRuntimeSettingsValue({ isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabled }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabled",
            "true"
        ).toBoolean()
    }

@Suppress("ObjectPropertyName")
private val _transformedClassesRegistry: TransformedClassesRegistry? =
    if (enabled) TransformedClassesRegistryImpl() else null

@Suppress("ObjectPropertyName")
private val _stacktraceElementsFactory: StacktraceElementsFactory? =
    if (enabled) StacktraceElementsFactoryImpl() else null

@Suppress("ObjectPropertyName")
private val _specMethodsFactory =
    if (enabled) loadService<SpecMethodsFactory>() ?: SpecMethodsFactoryImpl else null

internal val annotationMetadataResolver =
    if (enabled) loadService<AnnotationMetadataResolver>() else null

@Suppress("ObjectPropertyName")
private val _varHandleInvoker =
    if (enabled && methodHandleInvoker.supportsVarHandle) loadService<VarHandleInvoker>() else null

internal val supportsVarHandle = _varHandleInvoker != null

internal val transformedClassesRegistry: TransformedClassesRegistry
    get() = _transformedClassesRegistry!!

val methodHandleInvoker: MethodHandleInvoker
    get() = _methodHandleInvoker!!

val baseContinuationAccessorProvider: BaseContinuationAccessorProvider
    get() = _baseContinuationAccessorProvider!!

internal val stacktraceElementsFactory: StacktraceElementsFactory
    get() = _stacktraceElementsFactory!!

internal val specMethodsFactory: SpecMethodsFactory
    get() = _specMethodsFactory!!

internal val varHandleInvoker: VarHandleInvoker
    get() = _varHandleInvoker!!

private fun supportsMethodHandle(): Boolean {
    return try {
        _supportsMethodHandle().verify()
        true
    } catch (_: Throwable) {
        false
    }
}

@Suppress("ClassName")
@AndroidKeep
internal class _supportsMethodHandle {
    @Suppress("NewApi")
    fun verify() {
        val lookup = MethodHandles.lookup()
        val handle = lookup.findVirtual(
            _supportsMethodHandle::class.java,
            ::verify.name,
            MethodType.methodType(Void.TYPE)
        )
        assert { handle != null }
    }
}
