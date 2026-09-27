@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.latesttests.jvm.wrapcontinuationtests

import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.intrinsics.ownerClassName
import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi
import dev.reformator.stacktracedecoroutinator.latesttests.tests.checkStacktrace
import dev.reformator.stacktracedecoroutinator.latesttests.tests.runBlockingWithTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.test.assertEquals

class InstallDecoroutinatorLauncherSessionListener: LauncherSessionListener {
    override fun launcherSessionOpened(session: LauncherSession) {
        System.setProperty(
            "dev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy",
            "SYSTEM_RESOURCE"
        )
        DecoroutinatorJvmApi.install()
    }
}

class RuntimeTest: dev.reformator.stacktracedecoroutinator.latesttests.tests.RuntimeTest()
class TailCallDeoptimizeTest: dev.reformator.stacktracedecoroutinator.latesttests.tests.TailCallDeoptimizeTest()
class MethodNameWithSpacesTest: dev.reformator.stacktracedecoroutinator.latesttests.methodswithspacestests.MethodNameWithSpacesTest()
class TailCallDeoptimizedMethodNameWithSpacesTest: dev.reformator.stacktracedecoroutinator.latesttests.methodswithspacestests.TailCallDeoptimizedMethodNameWithSpacesTest()
class CustomClassLoaderTest: dev.reformator.stacktracedecoroutinator.latesttests.tests.CustomClassLoaderTest()
class CustomClassLoaderTailCallDeoptimizedTest: dev.reformator.stacktracedecoroutinator.latesttests.tests.CustomClassLoaderTailCallDeoptimizedTest()

class WrapContinuationTest {
    // Verifies the test setup itself (the enable-wrap-continuation plugin), not Decoroutinator: the continuation
    // passed from a tail-call suspend function to its callee has to be wrapped with TailCallBaseContinuationImpl.
    // Nothing gets suspended/resumed here, so Decoroutinator never gets involved.
    @Test
    fun wrapContinuationIsEnabled() = runBlockingWithTimeout {
        assertEquals(TAIL_CALL_BASE_CONTINUATION_CLASS_NAME, getContinuationClassNameTailCall())
    }

    @Test
    fun tailCallFrameRecovered() = runBlockingWithTimeout {
        tailCallFun1()
    }

    private suspend fun getContinuationClassNameTailCall(): String =
        getContinuationClassName()

    private suspend fun getContinuationClassName(): String =
        suspendCoroutineUninterceptedOrReturn { continuation -> continuation.javaClass.name }

    private suspend fun tailCallFun1() {
        tailCallFun1CallFun2LineNumber = currentLineNumber + 1
        tailCallFun2()
    }

    private suspend fun tailCallFun2() {
        yield()
        checkStacktrace(StackTraceElement(
            ownerClassName,
            ::tailCallFun1.name,
            currentFileName,
            tailCallFun1CallFun2LineNumber
        ))
    }

    private var tailCallFun1CallFun2LineNumber = 0
}

private const val TAIL_CALL_BASE_CONTINUATION_CLASS_NAME = "kotlin.coroutines.jvm.internal.TailCallBaseContinuationImpl"
