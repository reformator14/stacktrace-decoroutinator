@file:Suppress("PackageDirectoryMismatch", "JUnitMixedFramework")

package dev.reformator.stacktracedecoroutinator.tests.androidlegacy.gradleplugintests

import android.Manifest
import android.os.Build
import androidx.test.rule.GrantPermissionRule
import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.bytecodeprocessor.intrinsics.ownerClassName
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorCommonApi
import dev.reformator.stacktracedecoroutinator.tests.aar.suspendFunFromAar
import dev.reformator.stacktracedecoroutinator.tests.aar.suspendFunFromAarFileName
import dev.reformator.stacktracedecoroutinator.tests.aar.suspendFunFromAarLineNumber
import dev.reformator.stacktracedecoroutinator.tests.aar.suspendFunFromAarMethodName
import dev.reformator.stacktracedecoroutinator.tests.aar.suspendFunFromAarOwnerClassName
import dev.reformator.stacktracedecoroutinator.tests.checkStacktrace
import dev.reformator.stacktracedecoroutinator.tests.setRetraceMappingFiles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.coroutines.resume

open class TestLocalFile {
    @get:Rule
    val permissionRule: GrantPermissionRule
        get() = GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)

    @Before
    fun setup() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        setRetraceMappingFiles(mappingFile)
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
    @get:Rule
    val permissionRule: GrantPermissionRule
        get() = GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)

    @Before
    fun setup() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        setRetraceMappingFiles(mappingFile)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
open class DebugProbesTest {
    @Before
    fun setup() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
    }

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
    @get:Rule
    val permissionRule: GrantPermissionRule
        get() = GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)

    @Before
    fun setup() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        setRetraceMappingFiles(mappingFile)
    }
}

open class LegacyAndroidTest {
    @Before
    fun setup() {
        assumeTrue(Build.VERSION.SDK_INT < 26)
    }

    @Test
    fun checkStatus() {
        val status = DecoroutinatorCommonApi.getStatus { it() }
        assertFalse(status.successful, status.description)
    }
}

private val mappingFile: String
    @LoadConstant("mappingFile") get() = fail()
