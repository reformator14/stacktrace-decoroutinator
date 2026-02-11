@file:Suppress("PackageDirectoryMismatch")

package printspecmethod

import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.buildSpecMethodNode
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
fun main() {
    val classNode = ClassNode(Opcodes.ASM9).apply {
        version = Opcodes.V1_8
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER
        name = "dev/reformator/stacktracedecoroutinator/jvmcommon/StacktraceMethodPrinter"
        superName = Type.getInternalName(Object::class.java)
        sourceFile = "internal/unknown.kt"
    }
    classNode.methods = mutableListOf(buildSpecMethodNode(
        methodName = "stacktraceMethod",
        lineNumbers = setOf(10, 20, 25, 30),
        makePrivate = true,
        makeFinal = true
    ))

    val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    classNode.accept(classWriter)
    val classReader = ClassReader(classWriter.toByteArray())

    PrintWriter(System.out.writer()).use {
        classReader.accept(TraceClassVisitor(it), 0)
    }
}
