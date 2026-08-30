@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins.internal

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

val String.internalName: String
    get() = replace('.', '/')

internal fun List<AnnotationNode>?.find(type: Class<out Annotation>): AnnotationNode? =
    this?.find { it.desc == Type.getDescriptor(type) }

internal fun AnnotationNode.getParameter(name: String): Any? {
    var index = 0
    while (index < values.orEmpty().size) {
        if (values[index] == name) return values[index + 1]
        index += 2
    }
    return null
}

internal val MethodNode.isStatic: Boolean
    get() = access and Opcodes.ACC_STATIC != 0

internal val ClassNode.isInterface: Boolean
    get() = access and Opcodes.ACC_INTERFACE != 0

internal fun ClassNode.getOrCreateClinit(): MethodNode =
    methods?.firstOrNull {
        it.name == "<clinit>" && it.desc == "()V" && it.isStatic
    } ?: MethodNode(Opcodes.ASM9).apply {
        name = "<clinit>"
        desc = "()V"
        access = Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC
        instructions.add(InsnNode(Opcodes.RETURN))
        val methods: MutableList<MethodNode> = methods ?: run { methods = mutableListOf(); methods }
        methods.add(this)
    }

internal fun Class<*>.readAsm(): ClassNode =
    classLoader.getResourceAsStream(name.internalName + ".class")!!.use {
        val classReader = ClassReader(it)
        val classNode = ClassNode(Opcodes.ASM9)
        classReader.accept(classNode, 0)
        classNode
    }

internal infix fun MethodInsnNode.eq(other: MethodInsnNode): Boolean =
    opcode == other.opcode && owner == other.owner && name == other.name && desc == other.desc
