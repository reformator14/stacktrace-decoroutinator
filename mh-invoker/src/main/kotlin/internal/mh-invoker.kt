@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.mhinvoker.internal

import dev.reformator.bytecodeprocessor.intrinsics.MakeStatic
import dev.reformator.stacktracedecoroutinator.provider.internal.MethodHandleInvoker
import dev.reformator.stacktracedecoroutinator.provider.internal.VarHandleInvoker
import dev.reformator.stacktracedecoroutinator.intrinsics.assert
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidKeep
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.invoke.VarHandle

internal class RegularMethodHandleInvoker: MethodHandleInvoker {
    init {
        _support().verifyMethodHandleInvokeExact()
    }

    override val unknownSpecMethodHandle = getUnknownSpecMethodHandle()

    override fun callSpecMethod(handle: MethodHandle, spec: DecoroutinatorSpec, result: Any?): Any? =
        handle.invokeExact(spec, result)

    override val unknownSpecMethodClass: Class<*>
        get() = unknownSpecClass

    override val supportsVarHandle: Boolean =
        try {
            _supportVarHandle().verifyVarHandle()
            true
        } catch (_: Throwable) {
            false
        }
}

internal class RegularVarHandleInvoker: VarHandleInvoker {
    @Suppress("NewApi")
    override fun getIntVar(handle: VarHandle, owner: Any): Int =
        handle[owner] as Int
}

@Suppress("ClassName")
@AndroidKeep
private class _support {
    fun verifyMethodHandleInvokeExact() {
        val handle = MethodHandles.lookup().findStatic(
            _support::class.java,
            ::staticMethod.name,
            MethodType.methodType(Void.TYPE, _support::class.java)
        )
        handle.invokeExact(this)
    }
    @MakeStatic
    private fun staticMethod() { }
}

@Suppress("ClassName")
@AndroidKeep
private class _supportVarHandle {
    @Suppress("NewApi")
    fun verifyVarHandle() {
        val varHandle = MethodHandles.lookup().findVarHandle(
            _supportVarHandle::class.java,
            ::field.name,
            Int::class.javaPrimitiveType
        )
        val fieldValue = varHandle[this] as Int
        assert { fieldValue == 0 }
    }

    private var field: Int = 0
}
