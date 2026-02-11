@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("MhInvokerAndroidKt")

package dev.reformator.stacktracedecoroutinator.mhinvokerandroid

import android.util.Base64
import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dalvik.system.InMemoryDexClassLoader
import dev.reformator.stacktracedecoroutinator.common.internal.MethodHandleInvoker
import dev.reformator.stacktracedecoroutinator.common.internal.VarHandleInvoker
import java.nio.ByteBuffer

internal class AndroidMethodHandleInvoker: MethodHandleInvoker by loadRegularMethodHandleInvoker()

internal class AndroidVarHandleInvoker: VarHandleInvoker by loadRegularVarHandleInvoker()

private val regularMethodHandleDexBase64: String
    @LoadConstant("regularMethodHandleDexBase64") get() = fail()

private val String.decodeBase64: ByteArray
    get() = Base64.decode(this, Base64.DEFAULT)

@Suppress("NewApi")
private val regularMethodHandleLoader = InMemoryDexClassLoader(
    ByteBuffer.wrap(regularMethodHandleDexBase64.decodeBase64),
    MethodHandleInvoker::class.java.classLoader
)

private fun loadRegularMethodHandleInvoker(): MethodHandleInvoker =
    regularMethodHandleLoader.loadClass("dev.reformator.stacktracedecoroutinator.mhinvokerandroid.internal.RegularMethodHandleInvoker")
        .getDeclaredConstructor()
        .newInstance() as MethodHandleInvoker

private fun loadRegularVarHandleInvoker(): VarHandleInvoker =
    regularMethodHandleLoader.loadClass("dev.reformator.stacktracedecoroutinator.mhinvokerandroid.internal.RegularVarHandleInvoker")
        .getDeclaredConstructor()
        .newInstance() as VarHandleInvoker
