@file:Suppress("PackageDirectoryMismatch", "unused", "JUnitMixedFramework")

package dev.reformator.stacktracedecoroutinator.tests

import dev.reformator.bytecodeprocessor.intrinsics.*
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorCommonApi
import dev.reformator.stacktracedecoroutinator.tests.RuntimeTest.Companion.flushTraceOpcodeBuffer
import dev.reformator.stacktracedecoroutinator.tests.RuntimeTest.TraceOpcode
import dev.reformator.stacktracedecoroutinator.tests.RuntimeTest.TraceOpcodeItem
import dev.reformator.stacktracedecoroutinator.tests.duplicateentityjar.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.URI
import java.net.URLClassLoader
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

@Suppress("ConvertLongToDuration")
open class RuntimeTest {
    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.FUNCTION)
    annotation class TraceOpcode

    private val feature = AtomicReference<CompletableFuture<Unit>?>()
    private var recBaseLineNumber: Int = 0

    @Junit4Test @Junit5Test
    fun checkStatus() {
        val status = DecoroutinatorCommonApi.getStatus(true) { it() }
        assertTrue(status.successful, status.description)
    }

    @Junit4Test @Junit5Test
    fun inlineTransformedClassForKotlinc() {
        runBlocking {
            flowOf(1)
                .transform { emit(it) }
                .collect()
        }
    }

    @Junit4Test @Junit5Test
    fun basic() = runBlocking {
        val size = 30
        val lineNumberOffsets = run {
            val allowedLineNumberOffsets = listOf(0, 1, 3, 4, 6)
            val random = Random(123)
            generateSequence {
                allowedLineNumberOffsets.random(random)
            }.take(size).toList()
        }
        val job = launch {
            val result = rec(lineNumberOffsets, 0)
            val expectedResult = (0 until size).joinToString(separator = " ", postfix = " ")
            assertEquals(expectedResult, result)
        }
        while (feature.get() == null) {
            delay(10)
        }
        feature.get()!!.complete(Unit)
        job.join()
    }

    @Junit4Test @Junit5Test
    fun overloadedMethods() = runBlocking {
        overload(1)
        overload("")
    }

    @Junit4Test @Junit5Test
    fun resumeWithException() {
        var className = ""
        var methodName = ""
        var lineNumber = 0
        try {
            runBlocking {
                className = ownerClassName
                methodName = ownerMethodName
                lineNumber = currentLineNumber + 1
                suspendCoroutineUninterceptedOrReturn<Nothing> { continuation ->
                    thread {
                        continuation.resumeWithException(RuntimeException("test"))
                    }
                    COROUTINE_SUSPENDED
                }
            }
        } catch (e: RuntimeException) {
            e.stackTrace.checkStacktrace(StackTraceElement(
                className,
                methodName,
                currentFileName,
                lineNumber
            ))
        }
    }

    class CustomEx: Exception() {
        var rethrow = false
    }

    @Junit4Test @Junit5Test
    fun resumeDoubleException() {
        var firstResumeLineNumber = 0
        var secondResumeLineNumber = 0
        var resumeClassName = ""
        var resumeMethodName = ""
        try {
            runBlocking {
                try {
                    resumeClassName = ownerClassName
                    resumeMethodName = ownerMethodName
                    firstResumeLineNumber = currentLineNumber + 1
                    suspendCoroutineUninterceptedOrReturn { cont ->
                        cont.resumeWithException(CustomEx())
                        COROUTINE_SUSPENDED
                    }
                } catch (e: CustomEx) {
                    e.rethrow = true
                    secondResumeLineNumber = currentLineNumber + 1
                    suspendCoroutineUninterceptedOrReturn { cont ->
                        cont.resumeWithException(e)
                        COROUTINE_SUSPENDED
                    }
                }
            }
        } catch (e: CustomEx) {
            assertTrue(e.rethrow)
            val trace = e.stackTrace
            val secondResumeTraceStart = trace.getNextBoundaryIndex() + 1
            trace.checkStacktrace(
                StackTraceElement(
                    resumeClassName,
                    resumeMethodName,
                    currentFileName,
                    firstResumeLineNumber
                ),
                fromIndex = secondResumeTraceStart
            )
            val firstResumeTraceStart = trace.getNextBoundaryIndex(secondResumeTraceStart) + 1
            trace.checkStacktrace(
                StackTraceElement(
                    resumeClassName,
                    resumeMethodName,
                    currentFileName,
                    secondResumeLineNumber
                ),
                fromIndex = firstResumeTraceStart
            )
        }
    }

    @Junit4Test @Junit5Test
    fun testLoadSelfDefinedClass() {
        Class.forName("io.ktor.utils.io.ByteBufferChannel")
    }

    @Junit4Test @Junit5Test
    fun testSuspendCrossinlineInDifferentFile() {
        val flow = flow {
            for (i in 2..6) {
                emit(i)
                delay(10)
                emit(i * i * i)
            }
        }.transform {
            emit(it)
            delay(10)
            emit(it * it * it)
        }.transform {
            emit(it.toString())
            if (it == 5 * 5 * 5) {
                throw Exception("check")
            }
        }
        try {
            runBlocking {
                flow.collect { }
            }
        } catch (e: Exception) {
            assertEquals("check", e.message)
        }
    }

    @Junit4Test @Junit5Test
    fun loadInterfaceWithSuspendFunWithDefaultImpl() = runBlocking {
        object: InterfaceWithDefaultMethod { }.startCheck()
    }

    @Junit4Test @Junit5Test
    fun flowSingle(): Unit = runBlocking {
        val flow = flow {
            emit(10)
            yield()
        }
        flow.single()
    }

    @Junit4Test @Junit5Test
    fun concurrentTest() {
        val numThreads = Runtime.getRuntime().availableProcessors() * 2
        val numMocks = 10
        val random = Random(123)
        val tasks = List(numThreads) {
            val mocks = random.getConcurrentTestMocks(numMocks)
            Runnable {
                runBlocking {
                    callInline(mocks)
                }
            }
        }
        Executors.newFixedThreadPool(numThreads).let { executor ->
            try {
                val futures = tasks.map { executor.submit(it) }
                futures.forEach { it.get() }
            } finally {
                executor.shutdown()
            }
        }
    }

    @Junit4Test @Junit5Test
    fun withContextMustHaveDispatchedCoroutineResumeWithMethod(): Unit = runBlocking {
        withContext(Dispatchers.Default) {
            yield()
            assertTrue(Exception().stackTrace.asSequence().flatMap { it.getPossibleUnobfuscatedFrames() }.any { element ->
                element.className == "kotlinx.coroutines.DispatchedCoroutine" &&
                element.methodName == "resumeWith"
            })
        }
    }

    @Junit4Test @Junit5Test
    fun withContextMustNotDuplicateDispatchedCoroutineFrame(): Unit = runBlocking {
        withContext(Dispatchers.Default) {
            yield()
            val dispatchedCoroutineFrameCount = Exception().stackTrace.count { element ->
                element.getPossibleUnobfuscatedFrames().any { unobfuscated ->
                    unobfuscated.className == "kotlinx.coroutines.DispatchedCoroutine" &&
                    unobfuscated.methodName == "resumeWith"
                }
            }
            assertEquals(1, dispatchedCoroutineFrameCount)
        }
    }

    @Junit4Test @Junit5Test
    fun duplicateEntityJarTest() = runBlocking {
        val trace = duplicateEntityJarTailCallDeoptimizedTest {
            yield()
            Exception().stackTrace
        }
        trace.checkStacktrace(StackTraceElement(
            duplicateEntityJarTestClassName,
            duplicateEntityJarTailCallDeoptimizedTestMethodName,
            duplicateEntityJarTestFileName,
            duplicateEntityJarTailCallDeoptimizedTestLineNumber
        ))
    }

    @Suppress("DuplicatedCode")
    @Junit4Test @Junit5Test
    fun spiedMethodsLikeJacocoTransformedCorrectly() = runBlocking {
        flushTraceOpcodeBuffer()
        var trace: Array<StackTraceElement>? = null
        tracedSuspendFun {
            yield()
            trace = Thread.currentThread().stackTrace
        }
        trace!!.checkStacktrace(StackTraceElement(
            tracedSuspendFunClassName,
            tracedSuspendFunMethodName,
            currentFileName,
            tracedSuspendFunLineNumber
        ))
        val buffer = flushTraceOpcodeBuffer()
        val checkedItem = TraceOpcodeItem(
            className = tracedSuspendFunClassName,
            methodName = tracedSuspendFunMethodName,
            opcode = 193 // INSTANCEOF
        )
        assertTrue(checkedItem in buffer)
    }

    @Junit4Test @Junit5Test
    fun selfInstanceofSuspendFunction() = runBlocking {
        class SelfInstanceof {
            @Suppress("USELESS_CAST")
            suspend fun check() {
                yield()
                val self = this as? SelfInstanceof
                check(self != null)
            }
        }
        SelfInstanceof().check()
    }

    data class TraceOpcodeItem(val className: String, val methodName: String, val opcode: Int)

    companion object {
        @JvmStatic
        fun traceOpcode(className: String, methodName: String, opcode: Int) {
            traceOpcodeBuffer.get().add(TraceOpcodeItem(className, methodName, opcode))
        }

        fun flushTraceOpcodeBuffer(): List<TraceOpcodeItem> {
            val result = traceOpcodeBuffer.get()
            traceOpcodeBuffer.remove()
            return result
        }

        private val traceOpcodeBuffer = object: ThreadLocal<MutableList<TraceOpcodeItem>>() {
            override fun initialValue(): MutableList<TraceOpcodeItem> = LinkedList()
        }

        private suspend inline fun callInline(trace: List<ConcurrentTestMock>) {
            yield()
            if (trace.isNotEmpty()) {
                trace[0].call(trace.subList(1, trace.size))
            }
        }

        private fun tailCallDeoptimize() { }
    }

    private val concurrentTestMocks = listOf(
        ConcurrentTestMock1(), ConcurrentTestMock2(), ConcurrentTestMock3(), ConcurrentTestMock4(),
        ConcurrentTestMock5(), ConcurrentTestMock6(), ConcurrentTestMock7(), ConcurrentTestMock8(),
        ConcurrentTestMock9(), ConcurrentTestMock10(), ConcurrentTestMock11(), ConcurrentTestMock12(),
        ConcurrentTestMock13(), ConcurrentTestMock14(), ConcurrentTestMock15(), ConcurrentTestMock16(),
        ConcurrentTestMock17(), ConcurrentTestMock18(), ConcurrentTestMock19(), ConcurrentTestMock20()
    )

    private interface ConcurrentTestMock {
        suspend fun call(trace: List<ConcurrentTestMock>)
    }

    private class TestException(message: String): Exception(message)

    private class ConcurrentTestMock1: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock2: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock3: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock4: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock5: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock6: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock7: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock8: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock9: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock10: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock11: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock12: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock13: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock14: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock15: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock16: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock17: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock18: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock19: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }
    private class ConcurrentTestMock20: ConcurrentTestMock {
        override suspend fun call(trace: List<ConcurrentTestMock>) = callInline(trace)
    }

    private interface InterfaceWithDefaultMethod {
        suspend fun startCheck() {
            val lineNumber = currentLineNumber + 1
            check(StackTraceElement(
                ownerClassName,
                ownerMethodName,
                currentFileName,
                lineNumber
            ))
            tailCallDeoptimize()
        }

        suspend fun check(parent: StackTraceElement) {
            yield()
            checkStacktrace(parent)
        }
    }

    private lateinit var tracedSuspendFunClassName: String
    private lateinit var tracedSuspendFunMethodName: String
    private var tracedSuspendFunLineNumber = 0

    @TraceOpcode
    private suspend fun tracedSuspendFun(action: suspend() -> Unit) {
        tracedSuspendFunClassName = ownerClassName
        tracedSuspendFunMethodName = ownerMethodName
        tracedSuspendFunLineNumber = currentLineNumber + 1
        action()
        tailCallDeoptimize()
    }

    private fun Random.getConcurrentTestMocks(size: Int): List<ConcurrentTestMock> =
        buildList(size) {
            add(concurrentTestMocks.random(this@getConcurrentTestMocks))
        }

    private fun Array<StackTraceElement>.getNextBoundaryIndex(startIndex: Int = 0): Int {
        for (i in startIndex .. lastIndex) {
            val fileName = this[i].fileName
            if (fileName == "decoroutinator-boundary") return i
        }
        error("not found")
    }

    private suspend fun rec(lineNumberOffsets: List<Int>, index: Int): String {
        val checkedStacktrace = lineNumberOffsets.subList(0, index).reversed().map {
            StackTraceElement(
                ownerClassName,
                RuntimeTest::rec.name,
                currentFileName,
                recBaseLineNumber + it
            )
        }.toTypedArray()
        checkStacktrace(*checkedStacktrace)

        val message = if (index == lineNumberOffsets.size) {
            yield()
            val feature = CompletableFuture<Unit>()
            this.feature.set(feature)
            feature.await()
            ""
        } else {
            val previousMessage = try {
                recBaseLineNumber = currentLineNumber + 2
                val previousMessage = when (lineNumberOffsets[index]) {
                    0 -> rec(lineNumberOffsets, index + 1)
                    1 -> rec(lineNumberOffsets, index + 1)

                    3 -> rec(lineNumberOffsets, index + 1)
                    4 -> rec(lineNumberOffsets, index + 1)

                    6 -> rec(lineNumberOffsets, index + 1)

                    else -> throw IllegalArgumentException(
                        "invalid line number offset: ${lineNumberOffsets[index]} on index $index"
                    )
                }
                assertTrue(index % 2 == 1)
                previousMessage
            } catch (e: TestException) {
                assertTrue(index % 2 == 0)
                e.message!!
            }
            "$index $previousMessage"
        }

        checkStacktrace(*checkedStacktrace)

        if (index % 2 == 0) {
            return message
        } else {
            throw TestException(message)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun overload(par: Int) {
        val lineNumber = currentLineNumber + 1
        suspendResumeAndCheckStack(StackTraceElement(
            ownerClassName,
            ownerMethodName,
            currentFileName,
            lineNumber
        ))
        tailCallDeoptimize()
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun overload(par: String) {
        val lineNumber = currentLineNumber + 1
        suspendResumeAndCheckStack(StackTraceElement(
            ownerClassName,
            ownerMethodName,
            currentFileName,
            lineNumber
        ))
        tailCallDeoptimize()
    }

    private suspend fun suspendResumeAndCheckStack(vararg elements: StackTraceElement) {
        delay(10)
        checkStacktrace(*elements)
    }
}

open class TailCallDeoptimizeTest {
    @Junit4Test @Junit5Test
    fun checkStatus() {
        val status = DecoroutinatorCommonApi.getStatus { it() }
        assertTrue(status.successful, status.description)
    }

    @Junit4Test @Junit5Test
    fun basic() = runBlocking {
        tailCallDeoptimizeBasicRec(REC_DEPTH)
        tailCallDeoptF1()
    }

    @Junit4Test @Junit5Test
    fun interfaceWithDefaultMethodImpl() = runBlocking {
        object: InterfaceWithDefaultImplMethod {}.defaultImpl()
    }

    @Junit4Test @Junit5Test
    fun singleTailCall() = runBlocking {
        singleTailCallFun1()
    }

    @Junit4Test @Junit5Test
    fun duplicateEntityJarTest() = runBlocking {
        val trace = duplicateEntityJarTest {
            yield()
            Exception().stackTrace
        }
        trace.checkStacktrace(StackTraceElement(
            duplicateEntityJarTestClassName,
            duplicateEntityJarTestMethodName,
            duplicateEntityJarTestFileName,
            duplicateEntityJarTestLineNumber
        ))
    }

    @Suppress("DuplicatedCode")
    @Junit4Test @Junit5Test
    fun spiedMethodsLikeJacocoTransformedCorrectly() = runBlocking {
        flushTraceOpcodeBuffer()
        var trace: Array<StackTraceElement>? = null
        tracedSuspendFun {
            yield()
            trace = Thread.currentThread().stackTrace
        }
        trace!!.checkStacktrace(StackTraceElement(
            tracedSuspendFunClassName,
            tracedSuspendFunMethodName,
            currentFileName,
            tracedSuspendFunLineNumber
        ))
        val buffer = flushTraceOpcodeBuffer()
        val checkedItem = TraceOpcodeItem(
            className = tracedSuspendFunClassName,
            methodName = tracedSuspendFunMethodName,
            opcode = 185 // INVOKEINTERFACE
        )
        assertTrue(checkedItem in buffer)
    }

    private companion object {
        private const val REC_DEPTH = 10
    }

    private lateinit var tracedSuspendFunClassName: String
    private lateinit var tracedSuspendFunMethodName: String
    private var tracedSuspendFunLineNumber = 0

    @TraceOpcode
    private suspend fun tracedSuspendFun(action: suspend() -> Unit) {
        tracedSuspendFunClassName = ownerClassName
        tracedSuspendFunMethodName = ownerMethodName
        tracedSuspendFunLineNumber = currentLineNumber + 1
        action()
    }

    private interface InterfaceWithDefaultImplMethod {
        suspend fun defaultImpl() {
            val lineNumber = currentLineNumber + 1
            suspendFun(StackTraceElement(
                ownerClassName,
                ownerMethodName,
                currentFileName,
                lineNumber
            ))
        }

        suspend fun suspendFun(parent: StackTraceElement) {
            yield()
            checkStacktrace(parent)
        }
    }

    private lateinit var singleTailCallElement: StackTraceElement
    private var tailCallDeoptF1CallF2LineNumber: Int = 0
    private var recLineNumber = 0
    private var recRecLineNumber = 0

    private suspend fun singleTailCallFun1() {
        singleTailCallElement = StackTraceElement(
            ownerClassName,
            ownerMethodName,
            currentFileName,
            currentLineNumber + 2
        )
        singleTailCallFun2()
    }

    private suspend fun singleTailCallFun2() {
        yield()
        checkStacktrace(singleTailCallElement)
    }

    suspend fun tailCallDeoptF1() {
        tailCallDeoptF1CallF2LineNumber = currentLineNumber + 1
        tailCallDeoptF2()
    }

    suspend fun tailCallDeoptF2() {
        yield()
        checkStacktrace(StackTraceElement(
            ownerClassName,
            ::tailCallDeoptF1.name,
            currentFileName,
            tailCallDeoptF1CallF2LineNumber
        ))
    }

    private suspend fun tailCallDeoptimizeBasicRec(depth: Int) {
        if (depth > 0) {
            recLineNumber = currentLineNumber + 1
            tailCallDeoptimizeBasicRecRec(depth - 1)
        } else {
            suspendCoroutineUninterceptedOrReturn { cont ->
                ForkJoinPool.commonPool().execute {
                    cont.resume(Unit)
                }
                COROUTINE_SUSPENDED
            }
            val checkedStacktrace = (0 until REC_DEPTH * 2).map {
                val (methodName, lineNumber) = if (it % 2 == 0) {
                    ::tailCallDeoptimizeBasicRecRec.name to recRecLineNumber
                } else {
                    ::tailCallDeoptimizeBasicRec.name to recLineNumber
                }
                StackTraceElement(
                    ownerClassName,
                    methodName,
                    currentFileName,
                    lineNumber
                )
            }.toTypedArray()
            checkStacktrace(*checkedStacktrace)
        }
    }

    private suspend fun tailCallDeoptimizeBasicRecRec(depth: Int) {
        recRecLineNumber = currentLineNumber + 1
        tailCallDeoptimizeBasicRec(depth)
    }
}

open class CustomClassLoaderTest {
    @Junit5Test
    fun loadWithDecoroutinatorDependency() {
        loadCustomLoaderStubClass(true)
    }

    @Junit5Test
    fun loadWithoutDecoroutinatorDependency() {
        loadCustomLoaderStubClass(false)
    }

    @Junit5Test
    fun basic() {
        val clazz = loadCustomLoaderStubClass(true)
        val instance = clazz.getDeclaredConstructor().newInstance()
        val performCheckMethod = clazz.getDeclaredMethod("performCheck", Boolean::class.javaPrimitiveType)
        performCheckMethod.invoke(instance, true)
    }

    private companion object {
        private fun loadCustomLoaderStubClass(withDecoroutinatorDependency: Boolean): Class<*> =
            URLClassLoader(
                arrayOf(URI(customLoaderJarUri).toURL()),
                if (withDecoroutinatorDependency) ClassLoader.getSystemClassLoader() else null
            ).loadClass("dev.reformator.stacktracedecoroutinator.test.ClassWithSuspendFunctionsStub")
    }
}

open class CustomClassLoaderTailCallDeoptimizedTest {
    @Junit5Test
    fun basic() {
        val clazz = loadCustomLoaderStubClass()
        val instance = clazz.getDeclaredConstructor().newInstance()
        val performCheckMethod = clazz.getDeclaredMethod("performCheck", Boolean::class.javaPrimitiveType)
        performCheckMethod.invoke(instance, false)
    }

    private companion object {
        private fun loadCustomLoaderStubClass(): Class<*> =
            URLClassLoader(
                arrayOf(URI(customLoaderJarUri).toURL()),
                ClassLoader.getSystemClassLoader()
            ).loadClass("dev.reformator.stacktracedecoroutinator.test.ClassWithSuspendFunctionsStub")
    }
}

private val customLoaderJarUri: String
    @LoadConstant("customLoaderJarUri") get() { fail() }
