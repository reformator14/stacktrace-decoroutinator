@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.common.intrinsics.ContinuationImpl
import dev.reformator.stacktracedecoroutinator.common.intrinsics.createFailure
import dev.reformator.stacktracedecoroutinator.common.intrinsics.probeCoroutineResumed
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.intrinsics.UNKNOWN_LINE_NUMBER
import dev.reformator.stacktracedecoroutinator.provider.ContinuationCached
import dev.reformator.stacktracedecoroutinator.provider.SpecCache
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.callInvokeSuspend
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
import java.io.InputStream
import java.util.ServiceLoader
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

private fun <T: Any> loadService(type: Class<T>): T? {
    val iter: Iterator<T> = ServiceLoader.load(type).iterator()
    while (true) {
        try {
            if (!iter.hasNext()) {
                break
            }
            return iter.next()
        } catch (_: Throwable) { }
    }
    return null
}

internal inline fun <reified T: Any> loadService(): T? =
    loadService(T::class.java)

internal fun Class<*>.getBodyStream(loader: ClassLoader): InputStream? =
    loader.getResourceAsStream("${name.internalName}.class")

internal fun Class<*>.getBodyStream(): InputStream? =
    classLoader?.let { getBodyStream(it) }

internal const val ENABLED_PROPERTY = "dev.reformator.stacktracedecoroutinator.enabled"

internal class TailCallDeoptimizedContinuation(
    completion: Continuation<Any?>,
    override val `$decoroutinator$cache`: SpecCache
): ContinuationImpl(completion), ContinuationCached {
    override fun invokeSuspend(result: Any?): Any? =
        result

    override fun getStackTraceElement(): StackTraceElement? =
        `$decoroutinator$cache`.element
}

internal fun <K: Any, V: Any> MutableMap<K, V>.optimisticLockGet(key: K, notSetValue: V, lock: Lock): V? {
    val result = try {
        this[key]
    } catch (_: ConcurrentModificationException) { null } ?: lock.withLock {
        this[key]?.let { return@withLock it }
        this[key] = notSetValue
        notSetValue
    }
    return if (result === notSetValue) null else result
}

internal inline fun <K: Any, V: Any> MutableMap<K, V>.optimisticLockGetOrPut(
    key: K,
    lock: Lock,
    generator: () -> V
): V =
    try {
        this[key]
    } catch (_: ConcurrentModificationException) { null } ?: lock.withLock {
        this[key]?.let { return@withLock it }
        val newValue = generator()
        this[key] = newValue
        newValue
    }

internal class CompactMap<K, V>: AbstractMutableMap<K, V>() {
    private data class Entry<K, V>(override val key: K, override var value: V): MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            val oldValue = value
            value = newValue
            return oldValue
        }
    }

    private var _entries = emptyArray<Entry<K, V>>()

    override fun put(key: K, value: V): V? {
        _entries.forEach { entry ->
            if (entry.key == key) {
                val oldValue = entry.value
                entry.value = value
                return oldValue
            }
        }
        _entries += Entry(key, value)
        return null
    }

    override fun get(key: K): V? {
        _entries.forEach { (entryKey, entryValue) ->
            if (entryKey == key) {
                return entryValue
            }
        }
        return null
    }

    override val size: Int
        get() = _entries.size

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = object: AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override fun add(element: MutableMap.MutableEntry<K, V>): Boolean { TODO() }

            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
                object: MutableIterator<MutableMap.MutableEntry<K, V>> {
                    private var index = 0

                    override fun hasNext(): Boolean = index < _entries.size

                    override fun next(): MutableMap.MutableEntry<K, V> {
                        if (!hasNext()) throw NoSuchElementException()
                        return _entries[index++]
                    }

                    override fun remove() { TODO() }
                }

            override val size: Int
                get() = _entries.size
        }
}

internal fun <K, V1, V2> Map<K, V1>.mapValuesCompact(threshold: Int, transform: (Map.Entry<K, V1>) -> V2): Map<K, V2> {
    val result: MutableMap<K, V2> = if (size < threshold) {
        CompactMap()
    } else {
        newHashMapForSize(size)
    }
    mapValuesTo(result, transform)
    return result
}

internal fun <K, V> newHashMapForSize(size: Int): MutableMap<K, V> =
    HashMap(getHashMapCapacityForSize(size))

private fun getHashMapCapacityForSize(size: Int): Int =
    if (size < 3) 3 else (size * 4 / 3 + 1)

internal val StackTraceElement.normalizedLineNumber: Int
    get() = if (lineNumber < 0) UNKNOWN_LINE_NUMBER else lineNumber

internal fun BaseContinuation.callInvokeSuspend(
    accessor: BaseContinuationAccessor,
    result: Any?
): Any? =
    callInvokeSuspend(
        baseContinuation = this,
        accessor = accessor,
        result = result,
        probeCoroutineResumed = ::probeCoroutineResumed,
        createFailure = ::createFailure,
        coroutineSuspendedMarker = COROUTINE_SUSPENDED
    )
