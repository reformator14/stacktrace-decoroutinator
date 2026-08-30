@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorProviderInternalApiKt")

package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.MethodNameConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import java.lang.invoke.MethodHandles
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val prepareBaseContinuationAccessorLock = ReentrantLock()

@Suppress("ObjectPropertyName")
private var _baseContinuationAccessor: BaseContinuationAccessor? = null

val baseContinuationAccessor: BaseContinuationAccessor?
    @MethodNameConstant("getBaseContinuationAccessorMethodName") get() = _baseContinuationAccessor

@Suppress("NewApi")
@MethodNameConstant("prepareBaseContinuationAccessorMethodName")
fun prepareBaseContinuationAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor =
    prepareBaseContinuationAccessorLock.withLock {
        _baseContinuationAccessor?.let { return it }
        val accessor = baseContinuationAccessorProvider.createAccessor(lookup)
        _baseContinuationAccessor = accessor
        accessor
    }

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
