@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvm.tests

import dev.reformator.stacktracedecoroutinator.intrinsics.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi
import dev.reformator.stacktracedecoroutinator.jvm.internal.isTransformed
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val installDecoroutinator = System.getProperty("installDecoroutinator", "false").toBoolean()

class InstallDecoroutinatorLauncherSessionListener: LauncherSessionListener {
    override fun launcherSessionOpened(session: LauncherSession) {
        if (installDecoroutinator) {
            System.setProperty(
                "dev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy",
                "SYSTEM_RESOURCE"
            )
            DecoroutinatorJvmApi.install()
        }
    }
}

class RuntimeTest: dev.reformator.stacktracedecoroutinator.tests.RuntimeTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class TailCallDeoptimizeTest: dev.reformator.stacktracedecoroutinator.tests.TailCallDeoptimizeTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class MethodNameWithSpacesTest: dev.reformator.stacktracedecoroutinator.methodswithspacestests.MethodNameWithSpacesTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class TailCallDeoptimizedMethodNameWithSpacesTest: dev.reformator.stacktracedecoroutinator.methodswithspacestests.TailCallDeoptimizedMethodNameWithSpacesTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class CustomClassLoaderTest: dev.reformator.stacktracedecoroutinator.tests.CustomClassLoaderTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class CustomClassLoaderTailCallDeoptimizedTest: dev.reformator.stacktracedecoroutinator.tests.CustomClassLoaderTailCallDeoptimizedTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class PerformanceTest: dev.reformator.stacktracedecoroutinator.tests.PerformanceTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class ReloadBaseContinuationTest {
    @BeforeTest
    fun check() {
        assumeFalse(installDecoroutinator)
    }

    @Test
    fun reloadBaseContinuation() {
        val baseContinuationClass = Class.forName(BASE_CONTINUATION_CLASS_NAME)
        assertFalse(baseContinuationClass.isTransformed)
        DecoroutinatorJvmApi.install()
        assertTrue(baseContinuationClass.isTransformed)
    }
}
