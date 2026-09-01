@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.Base64
import java.util.zip.ZipInputStream

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal class AgentBaseContinuationAccessorProvider: BaseContinuationAccessorProvider {
    override fun createAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor {
        try {
            return loadRegularAccessor(lookup)
        } catch (_: Throwable) { }

        // No compile-time reference to the real kotlin.coroutines.jvm.internal.BaseContinuationImpl
        // type here (deliberately, so this stays correct even if this module's own kotlin.* usage
        // is ever relocated to a private package) - the actual class is resolved dynamically from
        // the caller-sensitive lookup, and the handles are widened to an Object-only shape via
        // asType() so invokeExact() below never needs a structural cast to the real type either.
        // lookup.lookupClass() IS BaseContinuationImpl directly: MethodHandles.lookup() is only
        // ever injected into BaseContinuationImpl's own resumeWith (transformBaseContinuation() in
        // class-transformer.kt is gated on node.name == BASE_CONTINUATION_CLASS_NAME, never a
        // subclass), so there's no ancestor to walk up to.
        val baseContinuationImplClass = lookup.lookupClass()
        // findVirtual's `type` describes only the method's own declared parameters (excluding the
        // implicit receiver) - the handle it returns actually takes (receiver, ...type's params...).
        val invokeSuspendHandle = lookup.findVirtual(
            baseContinuationImplClass,
            BaseContinuation::invokeSuspend.name,
            MethodType.methodType(Object::class.java, Object::class.java)
        ).asType(MethodType.methodType(Object::class.java, Object::class.java, Object::class.java))
        val releaseInterceptedHandle = lookup.findVirtual(
            baseContinuationImplClass,
            BaseContinuation::releaseIntercepted.name,
            MethodType.methodType(Void.TYPE)
        ).asType(MethodType.methodType(Void.TYPE, Object::class.java))
        return object: BaseContinuationAccessor {
            override fun invokeSuspend(baseContinuation: Any, result: Any?): Any? =
                invokeSuspendHandle.invokeExact(baseContinuation, result)

            override fun releaseIntercepted(baseContinuation: Any) {
                releaseInterceptedHandle.invokeExact(baseContinuation)
            }
        }
    }
}

private fun loadRegularAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor {
    var baseContinuationAccessorClass: Class<*>? = null
    ZipInputStream(Base64.getDecoder().decode(baseContinuationAccessorJarBase64).inputStream()).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            if (entry.name.endsWith(".class")) {
                val body = input.readBytes()
                lookup.defineClass(body).let { definedClass ->
                    if (definedClass.name == baseContinuationAccessorImplClassName) {
                        baseContinuationAccessorClass = definedClass
                    }
                }
            }
        }
    }
    return baseContinuationAccessorClass!!.getDeclaredConstructor().newInstance() as BaseContinuationAccessor
}

private val baseContinuationAccessorJarBase64: String
    @LoadConstant("baseContinuationAccessorJarBase64") get() { fail() }

private val baseContinuationAccessorImplClassName: String
    @LoadConstant("baseContinuationAccessorImplClassName") get() { fail() }
