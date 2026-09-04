@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.tests

import dev.reformator.stacktracedecoroutinator.tests.internal.R8Retrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

typealias Junit4Test = Test
typealias Junit5Test = org.junit.jupiter.api.Test

fun <T> runBlockingWithTimeout(
    timeout: Duration = 3.seconds,
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
