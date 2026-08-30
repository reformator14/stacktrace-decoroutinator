@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation

internal interface StacktraceElementsFactory {
    fun getStacktraceElement(baseContinuation: BaseContinuation): StackTraceElement?
    fun getLabel(baseContinuation: BaseContinuation): Int
}

internal const val NONE_LABEL = Int.MIN_VALUE / 2
internal const val UNKNOWN_LABEL = NONE_LABEL - 1
