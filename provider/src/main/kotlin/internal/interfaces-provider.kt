@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.io.InputStream
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

interface TransformedClassesRegistry {
    class TransformedClassSpec(
        val transformedClass: Class<*>,
        val className: String,
        val fileName: String?,
        val lookup: MethodHandles.Lookup,
        val methods: List<Method>
    ) {
        class Method(
            val methodName: String,
            val realMethodName: String,
            val lineNumbers: IntArray
        )
    }

    fun interface Listener {
        fun onNewTransformedClass(spec: TransformedClassSpec)
        fun onException(exception: Throwable) { }
    }

    val transformedClasses: Collection<TransformedClassSpec>
    operator fun get(clazz: Class<*>): TransformedClassSpec?
    fun addListener(listener: Listener)
    fun registerTransformedClass(lookup: MethodHandles.Lookup)
}

fun interface SpecMethodsFactory {
    fun getSpecMethodHandle(element: StackTraceElement): MethodHandle?
}

data class TransformationMetadata(
    val className: String,
    val fileName: String?,
    val methods: List<Method>
) {
    class Method(
        val name: String,
        val realName: String,
        val lineNumbers: IntArray
    )
}

@Suppress("ArrayInDataClass")
data class KotlinDebugMetadata(
    val sourceFile: String,
    val className: String,
    val methodName: String,
    val lineNumbers: IntArray
)

interface AnnotationMetadataResolver {
    fun getTransformationMetadata(classBody: InputStream): TransformationMetadata?
    fun getKotlinDebugMetadata(classBody: InputStream): KotlinDebugMetadata?
}

@AndroidLegacyKeep
interface MethodHandleInvoker {
    val unknownSpecMethodHandle: MethodHandle
    fun callSpecMethod(handle: MethodHandle, spec: DecoroutinatorSpec, result: Any?): Any?
    val unknownSpecMethodClass: Class<*>
    val supportsVarHandle: Boolean
}

@AndroidLegacyKeep
interface VarHandleInvoker {
    fun getIntVar(handle: VarHandle, owner: Any): Int
}
