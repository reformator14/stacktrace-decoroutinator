@file:Suppress("PackageDirectoryMismatch", "unused", "JUnitMixedFramework", "FunctionName")

package dev.reformator.stacktracedecoroutinator.methodswithspacestests

import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.intrinsics.ownerClassName
import dev.reformator.bytecodeprocessor.intrinsics.ownerMethodName
import dev.reformator.stacktracedecoroutinator.tests.checkStacktrace
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test

open class MethodNameWithSpacesTest {
    @Test
    fun `load method with spaces`() = runBlocking {
        yield()
    }

    @Test
    fun `method with spaces`() = runBlocking {
        yield()
        val lineNumber = currentLineNumber + 1
        checkStacktrace(StackTraceElement(
            ownerClassName,
            ownerMethodName,
            currentFileName,
            lineNumber
        ))
        tailCallDeoptimize()
    }

    private companion object {
        private fun tailCallDeoptimize() { }
    }
}

open class TailCallDeoptimizedMethodNameWithSpacesTest {
    @Test
    fun `method with spaces`() = runBlocking {
        val lineNumber = currentLineNumber + 1
        checkStacktrace(StackTraceElement(
            ownerClassName,
            ownerMethodName,
            currentFileName,
            lineNumber
        ))
    }
}