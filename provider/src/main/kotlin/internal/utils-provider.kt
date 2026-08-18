package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.lang.invoke.MethodHandle

val specLineNumberMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Int::class.javaPrimitiveType }!!
    .name

val isLastSpecMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Boolean::class.javaPrimitiveType }!!
    .name

@Suppress("NewApi")
val nextSpecHandleMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == MethodHandle::class.java }!!
    .name

val nextSpecMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == DecoroutinatorSpec::class.java }!!
    .name

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val resumeNextMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Object::class.java && it.parameterCount == 1 }!!
    .name

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val String.internalName: String
    get() = (this as java.lang.String).replace('.', '/')

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val String.binaryName: String
    get() = (this as java.lang.String).replace('/', '.')
