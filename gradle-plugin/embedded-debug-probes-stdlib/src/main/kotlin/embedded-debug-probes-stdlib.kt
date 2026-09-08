@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DebugProbesKt")

package kotlin.coroutines.jvm.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.loadService
import kotlin.coroutines.Continuation

interface DecoroutinatorDebugProbesProvider {
    fun <T> probeCoroutineCreated(completion: Continuation<T>): Continuation<T>
    fun probeCoroutineResumed(frame: Continuation<*>)
    fun probeCoroutineSuspended(frame: Continuation<*>)
}

// loadService (intrinsics module) searches both the thread's context classloader and
// DecoroutinatorDebugProbesProvider::class.java's own classloader - see that function's own doc.
private val provider: DecoroutinatorDebugProbesProvider =
    loadService<DecoroutinatorDebugProbesProvider>()!!

@Suppress("unused")
fun <T> probeCoroutineCreated(completion: Continuation<T>): Continuation<T> =
    provider.probeCoroutineCreated(completion)

@Suppress("unused")
fun probeCoroutineResumed(frame: Continuation<*>) {
    provider.probeCoroutineResumed(frame)
}

@Suppress("unused")
fun probeCoroutineSuspended(frame: Continuation<*>) {
    provider.probeCoroutineSuspended(frame)
}
