@file:Suppress("PackageDirectoryMismatch")

package kotlinx.coroutines.debug.internal

import dev.reformator.bytecodeprocessor.intrinsics.DeleteClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.runtimesettings.internal.getRuntimeSettingsValue
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.DecoroutinatorDebugProbesProvider

@Suppress("unused")
class DecoroutinatorDebugProbesProviderImpl: DecoroutinatorDebugProbesProvider {
    init {
        val enableCreationStackTraces = getRuntimeSettingsValue({ it.enableCreationStackTraces }) {
            System.getProperty(
                "dev.reformator.stacktracedecoroutinator.enableCreationStackTraces",
                "false"
            ).toBoolean()
        }
        val installDebugProbes = getRuntimeSettingsValue({ it.installDebugProbes }) {
            System.getProperty(
                "dev.reformator.stacktracedecoroutinator.installDebugProbes",
                "true"
            ).toBoolean()
        }
        AgentInstallationType.isInstalledStatically = true
        DebugProbesImpl.enableCreationStackTraces = enableCreationStackTraces
        if (installDebugProbes) {
            DebugProbesImpl.install()
        }
    }

    override fun <T> probeCoroutineCreated(completion: Continuation<T>): Continuation<T> =
        DebugProbesImpl.probeCoroutineCreated(completion)

    override fun probeCoroutineResumed(frame: Continuation<*>) {
        DebugProbesImpl.probeCoroutineResumed(frame)
    }

    override fun probeCoroutineSuspended(frame: Continuation<*>) {
        DebugProbesImpl.probeCoroutineSuspended(frame)
    }
}

@DeleteClass
@Suppress("UNUSED_PARAMETER")
private object DebugProbesImpl {
    var enableCreationStackTraces: Boolean
        @JvmName("getEnableCreationStackTraces\$kotlinx_coroutines_core")
        get() = fail()
        @JvmName("setEnableCreationStackTraces\$kotlinx_coroutines_core")
        set(value) { fail() }

    @JvmName("install\$kotlinx_coroutines_core")
    fun install() { fail() }

    @JvmName("probeCoroutineCreated\$kotlinx_coroutines_core")
    fun <T> probeCoroutineCreated(completion: Continuation<T>): Continuation<T> { fail() }

    @JvmName("probeCoroutineResumed\$kotlinx_coroutines_core")
    fun probeCoroutineResumed(frame: Continuation<*>) { fail() }

    @JvmName("probeCoroutineSuspended\$kotlinx_coroutines_core")
    fun probeCoroutineSuspended(frame: Continuation<*>) { fail() }
}

@DeleteClass
@Suppress("UNUSED_PARAMETER")
private object AgentInstallationType {
    var isInstalledStatically: Boolean
        @JvmName("isInstalledStatically\$kotlinx_coroutines_core")
        get() = fail()
        @JvmName("setInstalledStatically\$kotlinx_coroutines_core")
        set(value) { fail() }
}
