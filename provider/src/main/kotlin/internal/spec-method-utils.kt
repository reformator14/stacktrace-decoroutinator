@file:Suppress("PackageDirectoryMismatch")
@file:AndroidLegacyKeep

package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.lang.invoke.MethodType

@Suppress("NewApi", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val specMethodType: MethodType = MethodType.methodType(
    Object::class.java,
    DecoroutinatorSpec::class.java,
    Object::class.java
)
