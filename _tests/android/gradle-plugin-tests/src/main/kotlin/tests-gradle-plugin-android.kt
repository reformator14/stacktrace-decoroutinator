@file:Suppress("PackageDirectoryMismatch", "JUnitMixedFramework")

package dev.reformator.stacktracedecoroutinator.tests.android.gradleplugintests

import androidx.test.platform.app.InstrumentationRegistry
import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.intrinsics.ownerClassName
import dev.reformator.stacktracedecoroutinator.tests.aar.suspendFunFromAar
import dev.reformator.stacktracedecoroutinator.tests.aar.suspendFunFromAarFileName
import dev.reformator.stacktracedecoroutinator.tests.aar.suspendFunFromAarLineNumber
import dev.reformator.stacktracedecoroutinator.tests.aar.suspendFunFromAarMethodName
import dev.reformator.stacktracedecoroutinator.tests.aar.suspendFunFromAarOwnerClassName
import dev.reformator.stacktracedecoroutinator.tests.checkStacktrace
import dev.reformator.stacktracedecoroutinator.tests.readRetraceMappings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.coroutines.resume

open class TestLocalFile {
    @Before
    fun setup() {
        setRetraceMappings()
    }

    @Test
    fun localTest(): Unit = runBlocking {
        fun1()
    }

    @Test
    fun suspendFunFromAarTest() = runBlocking {
        val trace = suspendFunFromAar {
            yield()
            Exception().stackTrace
        }
        trace.checkStacktrace(StackTraceElement(
            suspendFunFromAarOwnerClassName,
            suspendFunFromAarMethodName,
            suspendFunFromAarFileName,
            suspendFunFromAarLineNumber
        ))
    }

    suspend fun fun1() {
        fun2(currentLineNumber)
    }

    suspend fun fun2(fun1LineNumber: Int) {
        val fun1Frame = StackTraceElement(
            ownerClassName,
            ::fun1.name,
            currentFileName,
            fun1LineNumber
        )
        yield()
        fun3(fun1Frame, currentLineNumber)
    }

    suspend fun fun3(fun1Frame: StackTraceElement, fun2LineNumber: Int) {
        val fun2Frame = StackTraceElement(
            ownerClassName,
            ::fun2.name,
            currentFileName,
            fun2LineNumber
        )
        yield()
        checkStacktrace(fun2Frame, fun1Frame)
    }
}

open class TailCallDeoptimizeTest: dev.reformator.stacktracedecoroutinator.tests.TailCallDeoptimizeTest() {
    @Before
    fun setup() {
        setRetraceMappings()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
open class DebugProbesTest {
    @Test
    open fun performDebugProbes() {
        runBlocking {
            suspendCancellableCoroutine { continuation ->
                assertTrue(DebugProbes.dumpCoroutinesInfo().isNotEmpty())
                continuation.resume(Unit)
            }
        }
    }
}

open class RuntimeTest: dev.reformator.stacktracedecoroutinator.tests.RuntimeTest() {
    @Before
    fun setup() {
        setRetraceMappings()
    }
}

private fun setRetraceMappings() {
    InstrumentationRegistry.getInstrumentation().targetContext.assets.open("mapping.txt").use { mapping ->
        readRetraceMappings(mapping)
    }
}