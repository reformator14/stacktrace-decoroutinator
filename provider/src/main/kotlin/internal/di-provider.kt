@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.assert
import dev.reformator.stacktracedecoroutinator.intrinsics.loadService
import dev.reformator.stacktracedecoroutinator.provider.SpecCache
import dev.reformator.stacktracedecoroutinator.runtimesettings.SpecChainBaseContinuationVerificationMode
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

@Suppress("ObjectPropertyName")
private val _specChainBaseContinuationVerificationMode =
    if (enabled) {
        // Workaround for a real R8 bug, not a style choice: if
        // 'loadSpecChainBaseContinuationVerificationMode' is declared to return
        // SpecChainBaseContinuationVerificationMode directly (instead of Any, cast here), R8 warns
        // "WARNING: R8: The method `void <...>.Di_providerKt.<clinit>()` does not type check and
        // will be assumed to be unreachable" - and actually replaces this whole <clinit> with a
        // stub that unconditionally throws, silently breaking every class in this file at
        // class-init time. Root-caused to R8's repackaging optimizer (RepackagingConstraintGraph):
        // a package-private-virtual-method-override-preservation check was missing an
        // isVirtualMethod() guard, so it also ran against non-virtual (static) method resolution
        // spanning a package boundary - matching this exact shape, where
        // SpecChainBaseContinuationVerificationMode lives in a different module (runtime-settings)
        // than this file (provider). Fixed upstream in R8 9.1.28-dev (commit 2b6832ee80726122a137
        // f4da63add9a317c39a0f, "Fix inadequate repackaging in presence of cross-package
        // <clinit>s"), first in a published release at 9.1.31 - see
        // https://issuetracker.google.com/issues/479862365 and
        // https://r8.googlesource.com/r8/+/2b6832ee80726122a137f4da63add9a317c39a0f. Still needed
        // here because current stable AGP (8.12.0) embeds R8 8.12.14, which predates the fix - can
        // be removed once the minimum supported AGP/R8 embeds 9.1.31 or newer.
        loadSpecChainBaseContinuationVerificationMode() as SpecChainBaseContinuationVerificationMode
    } else {
        null
    }

private fun loadSpecChainBaseContinuationVerificationMode(): Any =
    getRuntimeSettingsValue({ it.specChainBaseContinuationVerificationMode }) {
        SpecChainBaseContinuationVerificationMode.valueOf(System.getProperty(
            "dev.reformator.stacktracedecoroutinator.specChainBaseContinuationVerificationMode",
            SpecChainBaseContinuationVerificationMode.SHARED_VERIFY.name
        ))
    }

val specChainBaseContinuationVerificationMode: SpecChainBaseContinuationVerificationMode
    get() = _specChainBaseContinuationVerificationMode!!

val doVerifySharedSpec =
    enabled
    && specChainBaseContinuationVerificationMode == SpecChainBaseContinuationVerificationMode.SHARED_VERIFY

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
