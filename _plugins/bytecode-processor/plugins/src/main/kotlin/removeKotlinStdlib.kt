@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContext
import dev.reformator.bytecodeprocessor.api.ProcessingDirectory
import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.isInterface
import dev.reformator.bytecodeprocessor.plugins.internal.isStatic
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.io.Closeable
import java.util.Collections
import java.util.Objects
import kotlin.jvm.internal.Intrinsics
import kotlin.reflect.KFunction

object RemoveKotlinStdlibProcessor: Processor {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    override fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext) {
        directory.classes.forEach { clazz ->
            clazz.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode) {
                        if (
                            instruction.opcode == Opcodes.INVOKESTATIC
                            && instruction.owner == Type.getInternalName(Intrinsics::class.java)
                        ) {
                            if (
                                instruction.name in intrinsicCheckNotNullWithMessageMethodNames
                                && instruction.desc == "(${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(String::class.java)})V"
                            ) {
                                val prev = instruction.previous
                                val prevPrev = prev?.previous
                                if (prev.isPush1VarPureInstruction && prevPrev.isPush1VarPureInstruction) {
                                    method.instructions.remove(prev)
                                    method.instructions.remove(prevPrev)
                                } else {
                                    method.instructions.insert(instruction, InsnNode(Opcodes.POP2))
                                }
                                method.instructions.remove(instruction)
                                clazz.markModified()
                            } else if (
                                instruction.name in intrinsicThrowWithMessageMethodNames
                                && instruction.desc == "(${Type.getDescriptor(String::class.java)})${Type.VOID_TYPE.descriptor}"
                            ) {
                                val prev = instruction.previous
                                if (prev.isPush1VarPureInstruction) {
                                    method.instructions.remove(prev)
                                } else {
                                    method.instructions.insert(instruction, InsnNode(Opcodes.POP))
                                }
                                method.instructions.remove(instruction)
                                clazz.markModified()
                            } else if (
                                instruction.name in intrinsicCheckNotNullMethodNames
                                && instruction.desc == "(${Type.getDescriptor(Object::class.java)})${Type.VOID_TYPE.descriptor}"
                            ) {
                                val prev = instruction.previous
                                if (prev.isPush1VarPureInstruction) {
                                    method.instructions.remove(prev)
                                } else {
                                    method.instructions.insert(instruction, InsnNode(Opcodes.POP))
                                }
                                method.instructions.remove(instruction)
                                clazz.markModified()
                            } else if (
                                instruction.name in intrinsicAreEqualObjectsMethodNames
                                && instruction.desc == "(${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(Object::class.java)})${Type.BOOLEAN_TYPE.descriptor}"
                            ) {
                                instruction.owner = Type.getInternalName(Objects::class.java)
                                instruction.name = Objects::equals.name
                            }
                        } else if (
                            instruction.opcode == Opcodes.INVOKESTATIC
                            && instruction.owner == "kotlin/io/CloseableKt"
                            && instruction.name == "closeFinally"
                            && instruction.desc == "(${Type.getDescriptor(Closeable::class.java)}${Type.getDescriptor(Throwable::class.java)})${Type.VOID_TYPE.descriptor}"
                        ) {
                            val newMethod = clazz.node.getOrCreateCloseFinallyMethod()
                            instruction.owner = clazz.node.name
                            instruction.name = newMethod.name
                            clazz.markModified()
                        } else if (
                            instruction.opcode == Opcodes.INVOKESTATIC
                            && emptyCollectionFactoryDescs[instruction.owner to instruction.name] == instruction.desc
                        ) {
                            // kotlin.collections.{emptyMap,emptyList,emptySet} are real (non-inline) calls
                            // returning the same JDK collection types Collections.emptyXxx() does, under the
                            // exact same method name -> only the owner needs to change.
                            instruction.owner = Type.getInternalName(Collections::class.java)
                            clazz.markModified()
                        } else if (
                            instruction.opcode == Opcodes.INVOKESTATIC
                            && instruction.owner == "kotlin/collections/CollectionsKt"
                            && instruction.name == "collectionSizeOrDefault"
                            && instruction.desc == "(${Type.getDescriptor(Iterable::class.java)}I)I"
                        ) {
                            // the non-inline helper underneath map/mapIndexed/associate*/toHashSet/etc:
                            // internal fun <T> Iterable<T>.collectionSizeOrDefault(default: Int): Int =
                            //     if (this is Collection<*>) this.size else default
                            val newMethod = clazz.node.getOrCreateCollectionSizeOrDefaultMethod()
                            instruction.owner = clazz.node.name
                            instruction.name = newMethod.name
                            clazz.markModified()
                        }
                    } else if (instruction is FieldInsnNode) {
                        if (
                            instruction.opcode == Opcodes.GETSTATIC
                            && instruction.owner == "kotlin/_Assertions"
                            && instruction.name == "ENABLED"
                            && instruction.desc == Type.BOOLEAN_TYPE.descriptor
                        ) {
                            method.instructions.insert(instruction, InsnNode(Opcodes.ICONST_0))
                            method.instructions.remove(instruction)
                            clazz.markModified()
                        } else if (
                            instruction.opcode == Opcodes.GETSTATIC
                            && instruction.owner == Type.getInternalName(Unit::class.java)
                            && instruction.name == "INSTANCE"
                            && instruction.desc == Type.getDescriptor(Unit::class.java)
                        ) {
                            method.instructions.insert(instruction, InsnNode(Opcodes.ACONST_NULL))
                            method.instructions.remove(instruction)
                            clazz.markModified()
                        }
                    }
                }
            }
        }
        directory.module?.let { module ->
            if (module.node.requires?.removeIf { it.module == KOTLIN_STDLIB_MODULE } == true) {
                module.markModified()
            }
        }
    }
}

private const val KOTLIN_STDLIB_MODULE = "kotlin.stdlib"

private val intrinsicCheckNotNullWithMessageMethodNames = setOf(
    run {val x: (Any?, String) -> Unit = Intrinsics::checkNotNull; x as KFunction<*> }.name,
    Intrinsics::checkExpressionValueIsNotNull.name,
    Intrinsics::checkNotNullExpressionValue.name,
    run {val x: (Any?, String) -> Unit = Intrinsics::checkReturnedValueIsNotNull; x as KFunction<*> }.name,
    run {val x: (Any?, String) -> Unit = Intrinsics::checkFieldIsNotNull; x as KFunction<*> }.name,
    Intrinsics::checkParameterIsNotNull.name,
    Intrinsics::checkNotNullParameter.name
)

private val intrinsicThrowWithMessageMethodNames = setOf(
    run {val x: (String) -> Unit = Intrinsics::throwNpe; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::throwJavaNpe; x as KFunction<*>}.name,
    Intrinsics::throwUninitializedProperty.name,
    Intrinsics::throwUninitializedPropertyAccessException.name,
    run {val x: (String) -> Unit = Intrinsics::throwAssert; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::throwIllegalArgument; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::throwIllegalState; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::throwUndefinedForReified; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::needClassReification; x as KFunction<*>}.name,
)

private val intrinsicCheckNotNullMethodNames = setOf(
    run { val x: (Any) -> Unit = Intrinsics::checkNotNull; x as KFunction<*> }.name
)

private val intrinsicAreEqualObjectsMethodNames = setOf(
    run { val x: (Any?, Any?) -> Boolean = Intrinsics::areEqual; x as KFunction<*> }.name
)

// (owner, name) -> descriptor, for kotlin.collections top-level functions that are real (non-inline)
// calls to a JDK-interface-returning singleton, under the exact method name java.util.Collections uses.
private val emptyCollectionFactoryDescs = mapOf(
    ("kotlin/collections/MapsKt" to "emptyMap") to "()${Type.getDescriptor(Map::class.java)}",
    ("kotlin/collections/CollectionsKt" to "emptyList") to "()${Type.getDescriptor(List::class.java)}",
    ("kotlin/collections/SetsKt" to "emptySet") to "()${Type.getDescriptor(Set::class.java)}"
)

private val AbstractInsnNode?.isPush1VarPureInstruction: Boolean
    get() = (this is InsnNode && opcode == Opcodes.DUP)
        || (this is VarInsnNode && opcode == Opcodes.ALOAD)
        || (this is LdcInsnNode && opcode == Opcodes.LDC && cst is String)

private fun ClassNode.getOrCreateCloseFinallyMethod(): MethodNode {
    val name = "\$kotlin-stdlib\$closeFinally"
    val desc = "(${Type.getDescriptor(Closeable::class.java)}${Type.getDescriptor(Throwable::class.java)})${Type.VOID_TYPE.descriptor}"
    methods?.find { it.isStatic && it.name == name && it.desc == desc }?.let { return it }
    return createStaticMethod(name, desc).apply {
        instructions = InsnList().apply {
            add(VarInsnNode(Opcodes.ALOAD, 0))
            val nullLabel = LabelNode()
            add(JumpInsnNode(Opcodes.IFNULL, nullLabel))
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                Type.getInternalName(Closeable::class.java),
                Closeable::close.name,
                "()${Type.VOID_TYPE.descriptor}"
            ))
            add(nullLabel)
            add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
            add(InsnNode(Opcodes.RETURN))
        }
    }
}

private fun ClassNode.getOrCreateCollectionSizeOrDefaultMethod(): MethodNode {
    val name = "\$kotlin-stdlib\$collectionSizeOrDefault"
    val desc = "(${Type.getDescriptor(Iterable::class.java)}I)I"
    methods?.find { it.isStatic && it.name == name && it.desc == desc }?.let { return it }
    return createStaticMethod(name, desc).apply {
        instructions = InsnList().apply {
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(TypeInsnNode(Opcodes.INSTANCEOF, Type.getInternalName(Collection::class.java)))
            val notCollectionLabel = LabelNode()
            add(JumpInsnNode(Opcodes.IFEQ, notCollectionLabel))
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(TypeInsnNode(Opcodes.CHECKCAST, Type.getInternalName(Collection::class.java)))
            add(MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                Type.getInternalName(Collection::class.java),
                "size",
                "()${Type.INT_TYPE.descriptor}"
            ))
            add(InsnNode(Opcodes.IRETURN))
            add(notCollectionLabel)
            add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
            add(VarInsnNode(Opcodes.ILOAD, 1))
            add(InsnNode(Opcodes.IRETURN))
        }
    }
}

private fun ClassNode.createStaticMethod(name: String, desc: String): MethodNode {
    val makePrivate = !isInterface || version >= Opcodes.V9
    val makeFinal = !isInterface
    val result = MethodNode(
        Opcodes.ASM9,
        Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC or (if (makePrivate) Opcodes.ACC_PRIVATE else Opcodes.ACC_PUBLIC)
                or (if (makeFinal) Opcodes.ACC_FINAL else 0),
        name,
        desc,
        null,
        null
    )
    methods = methods.orEmpty() + result
    return result
}
