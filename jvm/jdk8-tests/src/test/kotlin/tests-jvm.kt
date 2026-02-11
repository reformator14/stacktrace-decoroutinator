@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvm.jdk8tests

import dev.reformator.stacktracedecoroutinator.intrinsics.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi
import dev.reformator.stacktracedecoroutinator.jvm.internal.isTransformed
import dev.reformator.stacktracedecoroutinator.methodswithspacestests.MethodNameWithSpacesTest
import dev.reformator.stacktracedecoroutinator.methodswithspacestests.TailCallDeoptimizedMethodNameWithSpacesTest
import dev.reformator.stacktracedecoroutinator.tests.CustomClassLoaderTailCallDeoptimizedTest
import dev.reformator.stacktracedecoroutinator.tests.CustomClassLoaderTest
import dev.reformator.stacktracedecoroutinator.tests.PerformanceTest
import dev.reformator.stacktracedecoroutinator.tests.RuntimeTest
import dev.reformator.stacktracedecoroutinator.tests.TailCallDeoptimizeTest
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

class RuntimeTest: RuntimeTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class TailCallDeoptimizeTest: TailCallDeoptimizeTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class MethodNameWithSpacesTest: MethodNameWithSpacesTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class TailCallDeoptimizedMethodNameWithSpacesTest: TailCallDeoptimizedMethodNameWithSpacesTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class CustomClassLoaderTest: CustomClassLoaderTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class CustomClassLoaderTailCallDeoptimizedTest: CustomClassLoaderTailCallDeoptimizedTest() {
    @BeforeTest
    fun check() {
        assumeTrue(installDecoroutinator)
    }
}

class PerformanceTest: PerformanceTest() {
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
