@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package dev.reformator.stacktracedecoroutinator.tests.jdk8.gradleplugintests

import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.stacktracedecoroutinator.tests.checkStacktrace
import dev.reformator.stacktracedecoroutinator.tests.runBlockingWithTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.*
import kotlin.coroutines.resume
import kotlin.test.Test

class PerformanceTest: dev.reformator.stacktracedecoroutinator.tests.PerformanceTest()
class RuntimeTest: dev.reformator.stacktracedecoroutinator.tests.RuntimeTest()
class TailCallDeoptimizeTest: dev.reformator.stacktracedecoroutinator.tests.TailCallDeoptimizeTest()
class MethodNameWithSpacesTest: dev.reformator.stacktracedecoroutinator.methodswithspacestests.MethodNameWithSpacesTest()
class TailCallDeoptimizedMethodNameWithSpacesTest: dev.reformator.stacktracedecoroutinator.methodswithspacestests.TailCallDeoptimizedMethodNameWithSpacesTest()

class TestLocalFile {
    @Test
    fun localTest(): Unit = runBlockingWithTimeout {
        fun1()
    }

    suspend fun fun1() {
        fun2(currentLineNumber)
    }

    suspend fun fun2(fun1LineNumber: Int) {
        val fun1Frame = StackTraceElement(
            TestLocalFile::class.qualifiedName,
            ::fun1.name,
            currentFileName,
            fun1LineNumber
        )
        fun3(fun1Frame, currentLineNumber)
    }

    suspend fun fun3(fun1Frame: StackTraceElement, fun2LineNumber: Int) {
        val fun2Frame = StackTraceElement(
            TestLocalFile::class.qualifiedName,
            ::fun2.name,
            currentFileName,
            fun2LineNumber
        )
        yield()
        checkStacktrace(fun2Frame, fun1Frame)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DebugProbesTest {
    @Test
    fun performDebugProbes() {
        runBlockingWithTimeout {
            suspendCancellableCoroutine { continuation ->
                assertTrue(DebugProbes.dumpCoroutinesInfo().isNotEmpty())
                continuation.resume(Unit)
            }
        }
    }
}
