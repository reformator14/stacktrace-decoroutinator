@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("ClassLoaderGeneratorKt")

package dev.reformator.stacktracedecoroutinator.generatorjvm.internal

import dev.reformator.bytecodeprocessor.intrinsics.MakeStatic
import dev.reformator.stacktracedecoroutinator.provider.internal.MethodHandleInvoker
import dev.reformator.stacktracedecoroutinator.provider.internal.specMethodType
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.buildSpecMethodNode
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

internal class DecoroutinatorClassLoader: ClassLoader(MethodHandleInvoker::class.java.classLoader) {
    fun buildClassAndGetSpecHandlesByMethod(
        className: String,
        fileName: String?,
        lineNumbersByMethod: Map<String, Set<Int>>
    ): Map<String, MethodHandle> {
        val body = getClassBody(
            className = className,
            fileName = fileName,
            lineNumbersByMethod = lineNumbersByMethod
        )
        val clazz = defineClass(className, body)
        return lineNumbersByMethod.mapValues { (methodName, _) ->
            MethodHandles.publicLookup().findStatic(clazz, methodName, specMethodType)
        }
    }

    @Suppress("unused")
    @MakeStatic(addToStaticInitializer = true)
    private fun clinit() {
        assert(registerAsParallelCapable())
    }

    private fun defineClass(className: String, classBody: ByteArray): Class<*> {
        synchronized(getClassLoadingLock(className)) {
            return defineClass(className, classBody, 0, classBody.size)
        }
    }
}

private fun getClassBody(
    className: String,
    fileName: String?,
    lineNumbersByMethod: Map<String, Set<Int>>
): ByteArray {
    val classNode = ClassNode(Opcodes.ASM9).apply {
        version = Opcodes.V1_8
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER
        name = className.internalName
        superName = Type.getInternalName(Object::class.java)
        sourceFile = fileName
        methods = lineNumbersByMethod.asSequence()
            .map { (methodName, lineNumbers) ->
                buildSpecMethodNode(
                    methodName = methodName,
                    lineNumbers = lineNumbers,
                    makePrivate = false,
                    makeFinal = true
                )
            }
            .toList()
    }
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classNode.accept(writer)
    return writer.toByteArray()
}
