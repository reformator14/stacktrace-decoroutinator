package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.assert
import dev.reformator.stacktracedecoroutinator.provider.SpecCache
import dev.reformator.stacktracedecoroutinator.runtimesettings.internal.getRuntimeSettingsValue
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

val supportsMethodHandle = supportsMethodHandle()

@Suppress("ObjectPropertyName")
val _methodHandleInvoker = if (supportsMethodHandle) loadService<MethodHandleInvoker>() else null

@Suppress("ObjectPropertyName")
val _baseContinuationAccessorProvider =
    if (_methodHandleInvoker != null) loadService<BaseContinuationAccessorProvider>() else null

val enabled =
    _baseContinuationAccessorProvider != null && getRuntimeSettingsValue({ it.enabled }) {
        System.getProperty(ENABLED_PROPERTY, "true").toBoolean()
    }

val tailCallDeoptimize =
    enabled && getRuntimeSettingsValue({ it.tailCallDeoptimize }) {
        System.getProperty("dev.reformator.stacktracedecoroutinator.tailCallDeoptimize", "true").toBoolean()
    }

val methodsNumberThreshold =
    if (enabled) {
        getRuntimeSettingsValue({ it.methodsNumberThreshold }) {
            System.getProperty(
                "dev.reformator.stacktracedecoroutinator.methodsNumberThreshold",
                "50"
            ).toInt()
        }
    } else 0

val fillUnknownElementsWithClassName =
    enabled && getRuntimeSettingsValue({ it.fillUnknownElementsWithClassName }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.fillUnknownElementsWithClassName",
            "true"
        ).toBoolean()
    }

val isUsingElementCacheForManualContinuationGetElementMethodEnabled =
    fillUnknownElementsWithClassName &&
    getRuntimeSettingsValue({ it.isUsingElementCacheForManualContinuationGetElementMethodEnabled }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isUsingElementCacheForManualContinuationGetElementMethodEnabled",
            "true"
        ).toBoolean()
    }

val isUsingElementFactoryForBaseContinuationEnabled: Boolean =
    enabled && getRuntimeSettingsValue({ it.isUsingElementFactoryForBaseContinuationEnabled }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isUsingElementFactoryForBaseContinuationEnabled",
            "true"
        ).toBoolean()
    }

val isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabled =
    fillUnknownElementsWithClassName &&
    getRuntimeSettingsValue({ it.isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabled }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isUsingElementCacheForLazilyCachedContinuationGetElementMethodEnabled",
            "true"
        ).toBoolean()
    }

@Suppress("ObjectPropertyName")
private val _transformedClassesRegistry: TransformedClassesRegistry? =
    if (enabled) TransformedClassesRegistryImpl() else null

@Suppress("ObjectPropertyName")
private val _specMethodsFactory =
    if (enabled) loadService<SpecMethodsFactory>() ?: SpecMethodsFactoryImpl else null

val annotationMetadataResolver =
    if (enabled) loadService<AnnotationMetadataResolver>() else null

@Suppress("ObjectPropertyName")
private val _varHandleInvoker =
    if (enabled && methodHandleInvoker.supportsVarHandle) loadService<VarHandleInvoker>() else null

val supportsVarHandle = _varHandleInvoker != null

val transformedClassesRegistry: TransformedClassesRegistry
    get() = _transformedClassesRegistry!!

val methodHandleInvoker: MethodHandleInvoker
    get() = _methodHandleInvoker!!

val baseContinuationAccessorProvider: BaseContinuationAccessorProvider
    get() = _baseContinuationAccessorProvider!!

val specMethodsFactory: SpecMethodsFactory
    get() = _specMethodsFactory!!

val varHandleInvoker: VarHandleInvoker
    get() = _varHandleInvoker!!

@Suppress("ObjectPropertyName")
private val _nullElementSpecCache: SpecCache? =
    if (enabled) {
        SpecCache(null).also { cache -> cache.specMethod = methodHandleInvoker.unknownSpecMethodHandle }
    } else null

val nullElementSpecCache: SpecCache
    get() = _nullElementSpecCache!!

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
