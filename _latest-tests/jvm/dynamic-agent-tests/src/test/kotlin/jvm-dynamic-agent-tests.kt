@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.latesttests.jvm.dynamicagenttests

import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi
import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener

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
