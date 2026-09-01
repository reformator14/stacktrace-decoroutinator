@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorProviderInternalApiKt")

package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.MethodNameConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import java.lang.invoke.MethodHandles

// Delegated to provider (not cached here) so the cache can be scoped per class loader by whatever
// implements DecoroutinatorProvider - see the KDoc on those two members for why a single,
// ungrouped cache is wrong.
@MethodNameConstant("getBaseContinuationAccessorMethodName")
fun getBaseContinuationAccessor(baseContinuation: Any): BaseContinuationAccessor? =
    provider.getBaseContinuationAccessor(baseContinuation)

@MethodNameConstant("prepareBaseContinuationAccessorMethodName")
fun prepareBaseContinuationAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor =
    provider.prepareBaseContinuationAccessor(lookup)

@MethodNameConstant("awakeBaseContinuationMethodName")
fun awakeBaseContinuation(accessor: BaseContinuationAccessor, baseContinuation: Any, result: Any?) {
    provider.awakeBaseContinuation(
        accessor = accessor,
        baseContinuation = baseContinuation,
        result = result
    )
}

@MethodNameConstant("getElementFactoryStacktraceElementMethodName")
fun getElementFactoryStacktraceElement(baseContinuation: Any): StackTraceElement? =
    provider.getElementFactoryStacktraceElement(baseContinuation)

val providerInternalApiClass: Class<*>
    @GetOwnerClass get() { fail() }
