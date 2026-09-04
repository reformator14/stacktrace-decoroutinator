@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.tests

import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.random.Random
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.time.Duration.Companion.seconds

sealed interface Mock {
    suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit)
}

suspend inline fun callTraceInline(trace: List<Mock>, noinline end: suspend () -> Unit) {
    if (trace.isEmpty()) {
        end()
    } else {
        trace[0].callTrace(trace.subList(1, trace.size), end)
    }
}

object Mock1: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock2: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock3: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock4: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock5: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock6: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock7: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock8: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock9: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock10: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock11: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock12: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock13: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock14: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock15: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock16: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock17: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock18: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock19: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock20: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock21: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock22: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock23: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock24: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock25: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock26: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock27: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock28: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock29: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock30: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock31: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock32: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock33: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock34: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock35: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock36: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock37: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock38: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock39: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock40: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock41: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock42: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock43: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock44: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock45: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock46: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock47: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock48: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock49: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}
object Mock50: Mock {
    override suspend fun callTrace(trace: List<Mock>, end: suspend () -> Unit) = callTraceInline(trace, end)
}

val mocks = listOf(Mock1, Mock2, Mock3, Mock4, Mock5, Mock6, Mock7, Mock8, Mock9, Mock10, Mock11, Mock12, Mock13,
    Mock14, Mock15, Mock16, Mock17, Mock18, Mock19, Mock20, Mock21, Mock22, Mock23, Mock24, Mock25, Mock26, Mock27,
    Mock28, Mock29, Mock30, Mock31, Mock32, Mock33, Mock34, Mock35, Mock36, Mock37, Mock38, Mock39, Mock40, Mock41,
    Mock42, Mock43, Mock44, Mock45, Mock46, Mock47, Mock48, Mock49, Mock50)

private fun Random.getMocks(depth: Int) = List(depth) { mocks.random(this) }

@Suppress("JUnitMixedFramework")
open class PerformanceTest {
    @Junit4Test @Junit5Test
    open fun depth10() {
        resumeWithDepth(10)
    }

    @Junit4Test @Junit5Test
    open fun depth50() {
        resumeWithDepth(50)
    }

    @Junit4Test @Junit5Test
    open fun depth100() {
        resumeWithDepth(100)
    }

    @Junit4Test @Junit5Test
    open fun depth500() {
        resumeWithDepth(500)
    }

    private fun resumeWithDepth(depth: Int) {
        val mocks = Random(1402).getMocks(depth)
        // 100 iterations x >=10ms sleep each is already >=1s - give this a generous timeout rather
        // than runBlockingWithTimeout's 3s default so it stays a hang safety-net, not a flaky failure.
        runBlockingWithTimeout(timeout = 30.seconds) {
            val times = mutableListOf<Long>()
            repeat(100) { index ->
                callTraceInline(mocks) {
                    var startResumeTime = 0L
                    suspendCoroutineUninterceptedOrReturn { continuation ->
                        thread {
                            Thread.sleep(10)
                            startResumeTime = System.nanoTime()
                            continuation.resume(Unit)
                        }
                        COROUTINE_SUSPENDED
                    }
                    val time = System.nanoTime() - startResumeTime
                    println("resume time for depth $depth #$index: $time ns")
                    times.add(time)
                }
            }
            times.sort()
            println("average time for depth $depth: ${times.sum() / times.size.toDouble()} ns")
            println("median time for depth $depth: ${(times[times.size / 2] + times[times.lastIndex / 2]) / 2.0} ns")
        }
    }
}

