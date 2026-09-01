package dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import java.io.InputStream

fun getClassNode(classBody: InputStream, skipCode: Boolean = false): ClassNode? {
    return try {
        val classReader = ClassReader(classBody)
        val classNode = ClassNode(Opcodes.ASM9)
        classReader.accept(classNode, if (skipCode) ClassReader.SKIP_CODE else 0)
        classNode
    } catch (_: Exception) {
        null
    }
}

val ClassNode.decoroutinatorTransformedAnnotation: AnnotationNode?
    get() = visibleAnnotations
        .orEmpty()
        .firstOrNull { it.desc == Type.getDescriptor(DecoroutinatorTransformed::class.java) }

fun AnnotationNode.getField(name: String): Any? {
    var index = 0
    while (index < values.orEmpty().size) {
        if (values[index] == name) {
            return values[index + 1]
        }
        index += 2
    }
    return null
}
