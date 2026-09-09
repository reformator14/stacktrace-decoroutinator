@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.stacktracedecoroutinator.provider.ChecksumFailedMarker
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.HasIntIdentity
import java.io.InputStream
import java.lang.invoke.MethodHandle
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock

const val ENABLED_PROPERTY = "dev.reformator.stacktracedecoroutinator.enabled"
const val CHECKSUM_VALID = 0
const val DEPTH_VALID = 0

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
    private var _entries = emptyArray<SimpleEntry<K, V>>()

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
        newEntries[_entries.size] = SimpleEntry(key, value)
        _entries = newEntries
        return null
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = java.util.LinkedHashSet(java.util.Arrays.asList(*_entries))
}

val StackTraceElement.hasLineNumber: Boolean
    get() = lineNumber >= 0

val specLineNumberMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Int::class.javaPrimitiveType && it.parameterCount == 0 }!!
    .name

@Suppress("NewApi")
val nextSpecHandleMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == MethodHandle::class.java && it.parameterCount == 0 }!!
    .name

val nextSpecMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == DecoroutinatorSpec::class.java && it.parameterCount == 0 }!!
    .name

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val resumeCookieMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Object::class.java && it.parameterCount == 0 }!!
    .name

val resumeChecksumMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Int::class.javaPrimitiveType && it.parameterCount == 2 }!!
    .name

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val resumeMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Object::class.java && it.parameterCount == 4 }!!
    .name

val checksumFailedMarkerMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == ChecksumFailedMarker::class.java && it.parameterCount == 0 }!!
    .name

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val String.internalName: String
    get() = (this as java.lang.String).replace('.', '/')

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
val String.binaryName: String
    get() = (this as java.lang.String).replace('/', '.')

inline fun resume(
    result: Any?,
    resumeChecksum: Int,
    resumeCookie: Any?,
    callInvokeSuspendIfResultIsNotCoroutineSuspended: (baseContinuation: Any, result: Any?) -> Any?
): Any? =
    when {
        // don't check depth checksum, because the contract of BaseContinuationImpl#resumeWith is still executed, even if
        // depth checksum is invalid. Depth checksum is needed to handle cycles.
        result === ChecksumFailedMarker || resumeChecksum != CHECKSUM_VALID -> {
            ChecksumFailedMarker
        }
        resumeCookie != null -> {
            callInvokeSuspendIfResultIsNotCoroutineSuspended(resumeCookie, result)
        }
        else -> {
            result
        }
    }

// MINUS_KEY_MULTIPLIER is PLUS_KEY_MULTIPLIER's exact modular multiplicative inverse mod 2^32
// (PLUS_KEY_MULTIPLIER * MINUS_KEY_MULTIPLIER == 1 mod 2^32, verified: pow(0x21f0aaad, -1, 1<<32) ==
// 0x333c4925) - both are odd, so each has a unique inverse mod 2^32. This is what makes minusKey an
// exact inverse of plusKey for the SAME key (see below), which is the whole point of using multiply
// (instead of e.g. plain xor/add) as the mixing step: multiplication by an odd constant is a bijection
// on 32-bit ints, so nothing about `plusKey` ever loses information that `minusKey` needs back.
private const val PLUS_KEY_MULTIPLIER = 0x21f0aaad
private const val MINUS_KEY_MULTIPLIER = 0x333c4925

// Together these implement a "stack of keys" folded into a single Int via a non-commutative, invertible
// mix, used by the resumeChecksum protocol (see AbstractExclusiveSpec/SharedSpec in provider-api.kt and
// common/awakener.kt's buildSharedSpecChain) to verify that a chain of shared spec objects is unwound in
// EXACTLY the nested order it was built, not merely with the same set of keys.
//   - For any A, K: (A plusKey K) minusKey K == A, always exactly (not just "likely") - algebraically,
//     minusKey(plusKey(A,K), K) = (((A*P) xor K) xor K) * M = (A*P)*M = A*(P*M) = A, since the two xors
//     with the same K cancel and P*M == 1 mod 2^32. This holds for every A and K, verified by exhaustive
//     algebra plus a 200k-sample randomized check.
//   - For any A, K1, K2: ((A plusKey K1) plusKey K2) minusKey K1 minusKey K2 (i.e. popping in the SAME
//     order the keys were pushed, rather than reverse/LIFO order) is virtually always != A - verified
//     empirically (0 matches in 200k random trials). This asymmetry is the actual point: correctly
//     unwinding a chain requires popping keys in the exact reverse order they were pushed (innermost
//     spec's key popped last, outermost's popped first, mirroring the real nested call structure), so any
//     out-of-order or substituted resume - e.g. a shared spec object silently reused by a different,
//     unrelated logical chain - almost certainly leaves a nonzero residue instead of exactly CHECKSUM_VALID.
infix fun Int.plusKey(key: Int): Int =
    (this * PLUS_KEY_MULTIPLIER) xor key

infix fun Int.minusKey(key: Int): Int =
    (this xor key) * MINUS_KEY_MULTIPLIER

val Any.intIdentity: Int
    get() = when (this) {
        is HasIntIdentity -> `$decoroutinator$intIdentity`
        else -> System.identityHashCode(this)
    }

fun randomInt(): Int =
    ThreadLocalRandom.current().nextInt()
