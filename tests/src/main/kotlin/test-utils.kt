@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.tests

import dev.reformator.stacktracedecoroutinator.tests.internal.R8Retrace
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

typealias Junit4Test = Test
typealias Junit5Test = org.junit.jupiter.api.Test

@Suppress("unused")
fun setRetraceMappingFiles(vararg files: String) {
    retraces = files.map { R8Retrace(File(it)) }
}

fun Array<StackTraceElement>.checkStacktrace(vararg expectedElements: StackTraceElement, fromIndex: Int = 0) {
    if (expectedElements.isEmpty()) {
        return
    }
    var startIndex = fromIndex
    while (!(this[startIndex].isFrame(expectedElements[0]))) startIndex++
    expectedElements.forEachIndexed { index, element ->
        assertTrue(this[startIndex + index].isFrame(element))
    }
}

fun checkStacktrace(vararg elements: StackTraceElement) {
    Exception().stackTrace.checkStacktrace(*elements)
}

fun StackTraceElement.getPossibleUnobfuscatedFrames() =
    sequenceOf(this) +
        retraces.asSequence().flatMap { it.getPossibleUnobfuscatedFrames(this) }

private var retraces = emptyList<R8Retrace>()

private fun StackTraceElement.isFrame(expectedFrame: StackTraceElement) =
    getPossibleUnobfuscatedFrames().any { unobfuscatedFrame ->
        unobfuscatedFrame.className == expectedFrame.className &&
            unobfuscatedFrame.methodName == expectedFrame.methodName &&
            unobfuscatedFrame.fileName == expectedFrame.fileName &&
            unobfuscatedFrame.lineNumber == expectedFrame.lineNumber
    }
