package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.UNKNOWN_LINE_NUMBER
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.io.InputStream
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodType
import java.util.ServiceLoader
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock

const val ENABLED_PROPERTY = "dev.reformator.stacktracedecoroutinator.enabled"

val specMethodType: MethodType = MethodType.methodType(
    Object::class.java,
    DecoroutinatorSpec::class.java,
    Object::class.java
)

fun <T: Any> loadService(type: Class<T>): T? {
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

inline fun <reified T: Any> loadService(): T? =
    loadService(T::class.java)

fun Class<*>.getBodyStream(loader: ClassLoader): InputStream? =
    loader.getResourceAsStream("${name.internalName}.class")

fun Class<*>.getBodyStream(): InputStream? =
    classLoader?.let { getBodyStream(it) }

fun <K: Any, V: Any> MutableMap<K, V>.optimisticLockGet(key: K, notSetValue: V, lock: Lock): V? {
    val result = try {
        this[key]
    } catch (_: ConcurrentModificationException) { null } ?: lock.withLock {
        this[key]?.let { return@withLock it }
        this[key] = notSetValue
        notSetValue
    }
    return if (result === notSetValue) null else result
}

inline fun <K: Any, V: Any> MutableMap<K, V>.optimisticLockGetOrPut(
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

fun <K, V> newHashMapForSize(size: Int): MutableMap<K, V> =
    HashMap(getHashMapCapacityForSize(size))

private fun getHashMapCapacityForSize(size: Int): Int =
    if (size < 3) 3 else (size * 4 / 3 + 1)

class CompactMap<K, V>: java.util.AbstractMap<K, V>() {
    private var _entries = emptyArray<java.util.AbstractMap.SimpleEntry<K, V>>()

    override val size: Int
        get() = _entries.size

    override fun get(key: K): V? {
        for (entry in _entries) {
            if (entry.key == key) return entry.value
        }
        return null
    }

    override fun put(key: K, value: V): V? {
        for (entry in _entries) {
            if (entry.key == key) {
                val oldValue = entry.value
                entry.setValue(value)
                return oldValue
            }
        }
        val newEntries = java.util.Arrays.copyOf(_entries, _entries.size + 1)
        newEntries[_entries.size] = java.util.AbstractMap.SimpleEntry(key, value)
        _entries = newEntries
        return null
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = java.util.LinkedHashSet(java.util.Arrays.asList(*_entries))
}

val StackTraceElement.normalizedLineNumber: Int
    get() = if (lineNumber < 0) UNKNOWN_LINE_NUMBER else lineNumber

val specLineNumberMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Int::class.javaPrimitiveType && it.parameterCount == 0 }!!
    .name

val isLastSpecMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Boolean::class.javaPrimitiveType && it.parameterCount == 0 }!!
    .name

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val nextSpecHandleMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == MethodHandle::class.java && it.parameterCount == 0 }!!
    .name

val nextSpecMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == DecoroutinatorSpec::class.java && it.parameterCount == 0 }!!
    .name

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val resumeNextMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Object::class.java && it.parameterCount == 1 }!!
    .name

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val String.internalName: String
    get() = (this as java.lang.String).replace('.', '/')

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val String.binaryName: String
    get() = (this as java.lang.String).replace('/', '.')

inline fun <reified BASE_CONTINUATION: Any> callInvokeSuspend(
    baseContinuation: BASE_CONTINUATION,
    accessor: BaseContinuationAccessor,
    result: Any?,
    probeCoroutineResumed: (BASE_CONTINUATION) -> Unit,
    createFailure: (Throwable) -> Any,
    coroutineSuspendedMarker: Any,
): Any? {
    probeCoroutineResumed(baseContinuation)
    val newResult = try {
        accessor.invokeSuspend(baseContinuation, result)
    } catch (exception: Throwable) {
        createFailure(exception)
    }
    if (newResult === coroutineSuspendedMarker) {
        return newResult
    }
    accessor.releaseIntercepted(baseContinuation)
    return newResult
}
