@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.tests

import dev.reformator.stacktracedecoroutinator.provider.internal.SpecMethodsFactoryImpl
import dev.reformator.stacktracedecoroutinator.provider.internal.specMethodsFactory
import dev.reformator.stacktracedecoroutinator.tests.internal.R8Retrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assume
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

typealias Junit4Test = Test
typealias Junit5Test = org.junit.jupiter.api.Test

// Every test in this module is dual-annotated (@Junit4Test @Junit5Test) so the same method can run
// under whichever framework a given pipeline's runner actually discovers it through - and those
// runners disagree on what counts as "this test was skipped, not failed". JUnit5's own
// org.junit.jupiter.api.Assumptions.assumeTrue/assumeFalse throw org.opentest4j.TestAbortedException,
// which a plain JUnit4 runner (e.g. Android's connectedAndroidTest, via
// androidx.test.runner.AndroidJUnitRunner - a genuine JUnit4 execution, no Jupiter engine/vintage
// bridge involved) doesn't recognize at all: it's just an uncaught exception, reported as a FAILURE,
// not a skip. JUnit4's own org.junit.Assume.assumeTrue/assumeFalse (AssumptionViolatedException) is
// the one choice that works everywhere this module's tests run: JUnit4 runners recognize it natively,
// and the JUnit5 Jupiter engine also recognizes it as an abort (via its own
// OpenTest4JAndJUnit4AwareThrowableCollector, built in specifically for this JUnit4/JUnit5
// interoperability case) - so route every assumption check in this module through these two
// functions rather than importing either framework's Assumptions/Assume directly at the call site.
fun assumeTrue(condition: Boolean) {
    Assume.assumeTrue(condition)
}

fun assumeFalse(condition: Boolean) {
    Assume.assumeFalse(condition)
}

// SpecMethodsFactoryImpl is a pure registry-lookup singleton with no bytecode/dex generation
// capability at all - the DI fallback when no BaseSpecMethodsFactory-derived generator
// (GeneratorJvmSpecMethodsFactory, AndroidSpecMethodsFactory) is on the classpath. Tests relying on
// the lazy runtime-generation fallback (e.g. for a class that failed registerTransformedClass
// registration) must skip rather than fail when this is the only implementation available.
//
// provider is only a compileOnly dependency here, so this reference resolves at runtime only where a
// real, unrelocated provider is actually on the classpath (e.g. the jvm self-install pipeline).
// jvm-agent's own test pipeline carries no such dependency at all - jvm-agent relocates its entire
// embedded provider copy under jvmagentjar.provider.internal.*, so this class genuinely can't be
// found there. That failure to resolve is itself the signal we're running under jvm-agent, which
// always embeds a real generator (generator-jvm) - so runtime generation is unconditionally
// available in that environment, hence the true fallback rather than treating it as disabled.
val isRuntimeGenerationOfSpecMethodsEnabled: Boolean
    get() = try {
        specMethodsFactory != SpecMethodsFactoryImpl
    } catch (_: ClassNotFoundException) {
        true
    } catch (_: NoClassDefFoundError) {
        true
    }

fun <T> runBlockingWithTimeout(
    timeout: Duration = 5.seconds,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T {
    val result = AtomicReference<Result<T>?>()
    val worker = thread {
        result.set(runCatching {
            runBlocking(context, block)
        })
    }
    worker.join(timeout.inWholeMilliseconds)
    worker.interrupt()
    return (result.get() ?: fail<Nothing>("block didn't finish within $timeout")).getOrThrow()
}

@Suppress("unused")
fun readRetraceMappings(vararg streams: InputStream) {
    retraces = streams.map { stream ->
        R8Retrace(stream.bufferedReader().lineSequence().iterator())
    }
}

fun Array<StackTraceElement>.checkStacktrace(vararg expectedElements: StackTraceElement, fromIndex: Int = 0) {
    if (expectedElements.isEmpty()) {
        return
    }
    var startIndex = fromIndex
    while (!(this[startIndex].isFrame(expectedElements[0]))) startIndex++
    expectedElements.forEachIndexed { index, element ->
        assertTrue(this[startIndex + index].isFrame(element))
    }
}

fun checkStacktrace(vararg elements: StackTraceElement) {
    Exception().stackTrace.checkStacktrace(*elements)
}

suspend fun suspendAndCheck(parent: StackTraceElement) {
    yield()
    checkStacktrace(parent)
}

fun StackTraceElement.getPossibleUnobfuscatedFrames() =
    sequenceOf(this) +
        retraces.asSequence().flatMap { it.getPossibleUnobfuscatedFrames(this) }

private var retraces = emptyList<R8Retrace>()

private fun StackTraceElement.isFrame(expectedFrame: StackTraceElement) =
    getPossibleUnobfuscatedFrames().any { unobfuscatedFrame ->
        unobfuscatedFrame.className == expectedFrame.className &&
            unobfuscatedFrame.methodName == expectedFrame.methodName &&
            unobfuscatedFrame.fileName == expectedFrame.fileName &&
            unobfuscatedFrame.lineNumber == expectedFrame.lineNumber
    }

// A shared "opaque predicate" backing both markReachableForR8 and preventR8FromInlining below:
// mathematically always 0 (Random.nextInt(bound) returns a value in [0, bound)), but computed via a
// genuine library call R8's optimizer has no special-cased understanding of - unlike e.g. a literal
// String's hashCode()/length(), which some optimizers do partially evaluate at compile time, nothing
// folds kotlin.random.Random's bound semantics into a known constant. That's the whole point: `zero
// != 0` has to stay a condition R8 cannot prove always resolves the same way, or it would just delete
// the "dead" branch below along with whatever it exists to keep alive. Computed once, at this file's
// own <clinit> time.
private val zero = Random.nextInt(1)

// R8 minification (Android's gradle-plugin-tests/android-legacy modules, isMinifyEnabled = true)
// does whole-program member-level shrinking: a method with zero real call sites anywhere -
// HasMissingDeclaredMethodSignatureType.neverCalled(), for instance - is provably dead code from R8's
// point of view and would simply be stripped from the compiled class entirely. If that happens, the
// class no longer even declares a method referencing MissingDeclaredMethodSignatureType (deleted by
// DeleteClassProcessor), so there's nothing left for Class.getDeclaredMethods() to trip over - the
// #87 regression test would silently stop testing anything on the one platform (Android) the original
// bug came from. Wrapping a real call in the `zero`-guarded, always-false branch above keeps the call
// site - and so the method, and its unresolvable-return-type signature - reachable, without ever
// actually executing it. This only solves the shrinking half of the problem - the consuming module's
// own proguard-rules.pro still needs a matching `-dontwarn dev.reformator.stacktracedecoroutinator.
// tests.MissingDeclaredMethodSignatureType`, or R8's class-hierarchy analysis errors on the
// now-reachable method's genuinely-unresolvable return type while processing its signature.
internal inline fun markReachableForR8(block: () -> Unit) {
    if (zero != 0) {
        block()
    }
}

// A live call site surviving shrinking (markReachableForR8, above) isn't enough on its own for a
// method as trivial as neverCalled() - R8's inliner can still substitute the call site with the
// method's own tiny body directly, at which point the original method declaration has no surviving
// reference left at all and gets stripped anyway, silently undoing markReachableForR8's whole point.
// Wrapping the method's real body so it also has a (dead, same `zero` guard) call back into itself
// defeats this: no inliner will substitute a call site with a body that calls the very method being
// inlined - that's unbounded self-reference, not a finite inlining, so the method has to stay a real,
// standalone declaration. HasMissingDeclaredMethodSignatureType.neverCalled()'s
// `preventR8FromInlining({ neverCalled() }) { fail() }` is this module's only real caller - the
// recursive branch is never actually taken, it exists purely to keep the method un-inlinable.
internal inline fun <T> preventR8FromInlining(recursiveCall: () -> T, realCall: () -> T): T =
    if (zero != 0) {
        recursiveCall()
    } else {
        realCall()
    }
