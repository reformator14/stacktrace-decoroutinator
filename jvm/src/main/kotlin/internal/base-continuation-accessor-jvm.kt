@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvm.internal

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.AgentBaseContinuationAccessorProviderRegularAccessorJarHolder

@Suppress("unused")
internal class BaseContinuationAccessorRegularJarHolder: AgentBaseContinuationAccessorProviderRegularAccessorJarHolder {
    override val regularAccessorJarBase64: String
        get() = baseContinuationAccessorJarBase64

    override val regularAccessorClassName: String
        get() = baseContinuationAccessorImplClassName
}

private val baseContinuationAccessorJarBase64: String
    @LoadConstant("baseContinuationAccessorJarBase64") get() { fail() }

private val baseContinuationAccessorImplClassName: String
    @LoadConstant("baseContinuationAccessorImplClassName") get() { fail() }
