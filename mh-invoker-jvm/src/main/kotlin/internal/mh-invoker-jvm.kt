@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("MhInvokerJvmKt")

package dev.reformator.stacktracedecoroutinator.mhinvokerjvm.internal

import dcunknownjvm.getUnknownPackageLookup
import dcunknownjvm.getUnknownPackageName
import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.bytecodeprocessor.intrinsics.ownerClass
import dev.reformator.stacktracedecoroutinator.provider.internal.MethodHandleInvoker
import dev.reformator.stacktracedecoroutinator.provider.internal.VarHandleInvoker
import java.lang.invoke.MethodHandles
import java.util.Base64
import java.util.zip.ZipInputStream

internal class JvmMethodHandleInvoker: MethodHandleInvoker by
    loader.loadClass(
        "dev.reformator.stacktracedecoroutinator.mhinvokerjvm.internal.RegularMethodHandleInvoker"
    ).getDeclaredConstructor().newInstance() as MethodHandleInvoker

internal class JvmVarHandleInvoker: VarHandleInvoker by
    loader.loadClass(
        "dev.reformator.stacktracedecoroutinator.mhinvokerjvm.internal.RegularVarHandleInvoker"
    ).getDeclaredConstructor().newInstance() as VarHandleInvoker

private val loader: ClassLoader = run {
    val classes = getRegularMethodHandleInvokerClasses()
    try {
        val internalPackageLookup = MethodHandles.lookup()
        val unknownPackageLookup = getUnknownPackageLookup()
        val internalPackageName = ownerClass.packageName
        val unknownPackageName = getUnknownPackageName()
        classes.forEach { (name, body) ->
            val lookup = when (name.substringBeforeLast('.')) {
                internalPackageName -> internalPackageLookup
                unknownPackageName -> unknownPackageLookup
                else -> error("Unexpected package name in class [$name]")
            }
            lookup.defineClass(body)
        }
        JvmMethodHandleInvoker::class.java.classLoader
    } catch (jpmsEx: Throwable) {
        try {
            DecoroutinatorMhInvokerJvmClassLoader(classes)
        } catch (e: Throwable) {
            e.addSuppressed(jpmsEx)
            throw e
        }
    }
}

private val regularMethodHandleJarBase64: String
    @LoadConstant("regularMethodHandleJarBase64") get() { fail() }

private val String.decodeBase64: ByteArray
    get() = Base64.getDecoder().decode(this)

private fun getRegularMethodHandleInvokerClasses(): Map<String, ByteArray> = buildMap {
    ZipInputStream(regularMethodHandleJarBase64.decodeBase64.inputStream()).use { input ->
        while (true) {
            val entry = input.nextEntry ?: return@use
            if (entry.name.endsWith(".class")) {
                put(entry.name.removeSuffix(".class").replace("/", "."), input.readBytes())
            }
        }
    }
}

private class DecoroutinatorMhInvokerJvmClassLoader(
    private val classes: Map<String, ByteArray>
): ClassLoader(MethodHandleInvoker::class.java.classLoader) {
    override fun findClass(name: String): Class<*> =
        classes[name]?.let { entity ->
            defineClass(name, entity, 0, entity.size)
        } ?: super.findClass(name)
}
