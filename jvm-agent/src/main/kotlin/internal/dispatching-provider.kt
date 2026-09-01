@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagent.internal

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.intrinsics.CONTINUATION_INTERFACE_NAME
import dev.reformator.stacktracedecoroutinator.provider.SpecCache
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider
import dev.reformator.stacktracedecoroutinator.provider.internal.optimisticLockGetOrPut
import dev.reformator.stacktracedecoroutinator.runtimesettings.internal.getRuntimeSettingsValue
import java.lang.invoke.MethodHandles
import java.util.Base64
import java.util.WeakHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipInputStream

// DecoroutinatorProvider implementation bundled with the jvm-agent shaded jar. common's own
// Provider (kotlin.coroutines.jvm.internal.*-bound) can only safely run under a classloader that
// resolves the SAME kotlin-stdlib copy a given coroutine class's own classloader would resolve.
// This dispatches every call to a private copy of common's residual, embedded (see below) and
// defined into (parented on) whichever classloader the call's own object actually belongs to.
internal class DispatchingProvider: DecoroutinatorProvider {
    private val defaultLoader: ClassLoader =
        DispatchingProvider::class.java.classLoader ?: ClassLoader.getSystemClassLoader()

    // Common case: the agent's own loader already resolves a real kotlin-stdlib (normally the
    // host application's), so a single copy parented on it behaves exactly like today - no need
    // to pay for per-classloader tracking. Built eagerly here (not `by lazy`) rather than lazily
    // in entryFor's fast path below: entryFor runs on every single call in this common case, and
    // `by lazy`'s thread-safe mode does a volatile read on every access to check whether it's
    // already initialized - cheap on x86, not free on ARM. A plain nullable val, set once at
    // construction, costs nothing beyond an ordinary field read.
    private val defaultEntry: DecoroutinatorProvider? =
        if (forceAgentClassLoaderDispatchingProvider || !hasKotlinStdlibVisible(defaultLoader)) {
            null
        } else {
            buildEntry(defaultLoader)
        }

    private val entriesByLoaderLock = ReentrantLock()

    // Weakly keyed so an isolated/plugin classloader that becomes unreachable can still be
    // collected - the agent must not pin arbitrary application classloaders alive forever. Not
    // synchronized: entryFor below only ever accesses it through optimisticLockGetOrPut, which
    // handles its own concurrency (an unsynchronized optimistic read, falling back to
    // entriesByLoaderLock on a miss or a raced ConcurrentModificationException) - the same
    // pattern already used elsewhere in this codebase (see optimisticLockGetOrPut's own doc).
    // Only allocated when defaultEntry above is null (dispatching is actually needed).
    private val entriesByLoader: MutableMap<ClassLoader, DecoroutinatorProvider>? =
        if (defaultEntry == null) WeakHashMap() else null

    private fun entryFor(loader: ClassLoader?): DecoroutinatorProvider {
        defaultEntry?.let { return it }
        val key = loader ?: defaultLoader
        return entriesByLoader!!.optimisticLockGetOrPut(key, entriesByLoaderLock) {
            val kotlinStdlibLoader = key.loadClass(CONTINUATION_INTERFACE_NAME).classLoader!!
            entriesByLoader[kotlinStdlibLoader]?.let { return@optimisticLockGetOrPut it }
            val result = buildEntry(kotlinStdlibLoader)
            entriesByLoader[kotlinStdlibLoader] = result
            result
        }
    }

    override fun awakeBaseContinuation(accessor: BaseContinuationAccessor, baseContinuation: Any, result: Any?) {
        entryFor(baseContinuation.javaClass.classLoader).awakeBaseContinuation(accessor, baseContinuation, result)
    }

    override fun tailCallDeoptimize(completion: Any, cache: SpecCache?): Any =
        entryFor(completion.javaClass.classLoader).tailCallDeoptimize(completion, cache)

    override fun getElementFactoryStacktraceElement(baseContinuation: Any): StackTraceElement? =
        entryFor(baseContinuation.javaClass.classLoader).getElementFactoryStacktraceElement(baseContinuation)

    override fun getCoroutineStackFrameStackTraceElement(coroutineStackFrame: Any): StackTraceElement? =
        entryFor(coroutineStackFrame.javaClass.classLoader).getCoroutineStackFrameStackTraceElement(coroutineStackFrame)

    override fun callInvokeSuspendIfResultIsNotCoroutineSuspended(
        baseContinuation: Any,
        accessor: BaseContinuationAccessor,
        result: Any?
    ): Any? =
        entryFor(baseContinuation.javaClass.classLoader).callInvokeSuspendIfResultIsNotCoroutineSuspended(
            baseContinuation = baseContinuation,
            accessor = accessor,
            result = result
        )

    override fun getBaseContinuationAccessor(baseContinuation: Any): BaseContinuationAccessor? =
        entryFor(baseContinuation.javaClass.classLoader).getBaseContinuationAccessor(baseContinuation)

    // lookup.lookupClass() is always the BaseContinuationImpl this was injected into (see the
    // comment on transformBaseContinuation() in class-transformer.kt), so its class loader is the
    // same routing key getBaseContinuationAccessor above uses via baseContinuation - both land on
    // the same common.internal.Provider instance and its cache.
    override fun prepareBaseContinuationAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor =
        entryFor(lookup.lookupClass().classLoader).prepareBaseContinuationAccessor(lookup)
}

private fun buildEntry(targetLoader: ClassLoader): DecoroutinatorProvider {
    val loader = DecoroutinatorCommonClassLoader(commonResidualClasses, targetLoader)
    val providerClass = loader.loadClass("dev.reformator.stacktracedecoroutinator.common.internal.Provider")
    return providerClass.getDeclaredConstructor().newInstance() as DecoroutinatorProvider
}

private class DecoroutinatorCommonClassLoader(
    private val classes: Map<String, ByteArray>,
    parent: ClassLoader
): ClassLoader(parent) {
    override fun findClass(name: String): Class<*> =
        classes[name]?.let { entity -> defineClass(name, entity, 0, entity.size) } ?: super.findClass(name)
}

private fun hasKotlinStdlibVisible(loader: ClassLoader): Boolean =
    try {
        Class.forName("kotlin.coroutines.jvm.internal.CoroutineStackFrame", false, loader)
        true
    } catch (_: Throwable) {
        false
    }

private val forceAgentClassLoaderDispatchingProvider: Boolean =
    getRuntimeSettingsValue({ it.forceAgentClassLoaderDispatchingProvider }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.forceAgentClassLoaderDispatchingProvider",
            "false"
        ).toBoolean()
    }

// common's base64-encoded jar exceeds the JVM's 65535-byte CONSTANT_Utf8_info limit for a single
// string constant, so it's split across a fixed number of chunk constants at build time (see
// commonResidualJarBase64ChunkCount in jvm-agent/build.gradle.kts) and concatenated here. Unused
// trailing chunks are filled with an empty string by the build task.
private val commonResidualJarBase64Chunk0: String
    @LoadConstant("commonResidualJarBase64Chunk0") get() { fail() }
private val commonResidualJarBase64Chunk1: String
    @LoadConstant("commonResidualJarBase64Chunk1") get() { fail() }
private val commonResidualJarBase64Chunk2: String
    @LoadConstant("commonResidualJarBase64Chunk2") get() { fail() }
private val commonResidualJarBase64Chunk3: String
    @LoadConstant("commonResidualJarBase64Chunk3") get() { fail() }
private val commonResidualJarBase64Chunk4: String
    @LoadConstant("commonResidualJarBase64Chunk4") get() { fail() }
private val commonResidualJarBase64Chunk5: String
    @LoadConstant("commonResidualJarBase64Chunk5") get() { fail() }
private val commonResidualJarBase64Chunk6: String
    @LoadConstant("commonResidualJarBase64Chunk6") get() { fail() }
private val commonResidualJarBase64Chunk7: String
    @LoadConstant("commonResidualJarBase64Chunk7") get() { fail() }

private val commonResidualJarBase64: String
    get() = commonResidualJarBase64Chunk0 +
        commonResidualJarBase64Chunk1 +
        commonResidualJarBase64Chunk2 +
        commonResidualJarBase64Chunk3 +
        commonResidualJarBase64Chunk4 +
        commonResidualJarBase64Chunk5 +
        commonResidualJarBase64Chunk6 +
        commonResidualJarBase64Chunk7

private val commonResidualClasses: Map<String, ByteArray> by lazy {
    buildMap {
        ZipInputStream(Base64.getDecoder().decode(commonResidualJarBase64).inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: return@use
                if (entry.name.endsWith(".class") && entry.name != "module-info.class") {
                    put(entry.name.removeSuffix(".class").replace("/", "."), input.readBytes())
                }
            }
        }
    }
}
