@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.intrinsics

import java.util.ServiceLoader

// Searches BOTH the thread's context classloader (ServiceLoader.load(type) - the exact same single-arg
// call the old, pre-fix code used, kept verbatim rather than manually reconstructed as
// ServiceLoader.load(type, Thread.currentThread().contextClassLoader) so this leg's behavior can never
// subtly diverge from what it always was - needed for a consumer-supplied service that isn't reachable
// from wherever `type` itself was loaded from) and `type`'s own classloader (ServiceLoader.load(type,
// type.classLoader) - needed once `type` ends up loaded somewhere the context classloader doesn't
// delegate to) - deduplicated by implementation class, since a provider registered where both loaders
// can see it would otherwise surface twice.
// inline (with an inline `action` parameter, not `noinline`/`crossinline`) so this has zero runtime
// dependency on intrinsics.jar - its whole body, `action` included, is copied directly into the caller,
// same as `assert`/`ifAssertionEnabled` already rely on for the same reason.
inline fun <reified T: Any> loadServices(action: (T) -> Unit) {
    val type = T::class.java
    val seenClasses = java.util.HashSet<Class<*>>()
    processServiceIterator(
        iterProducer = { ServiceLoader.load(type).iterator() },
        seenClasses = seenClasses,
        action = action
    )
    processServiceIterator(
        iterProducer = { ServiceLoader.load(type, T::class.java.classLoader).iterator() },
        seenClasses = seenClasses,
        action = action
    )
}

@PublishedApi
internal inline fun <T: Any> processServiceIterator(
    iterProducer: () -> Iterator<T>,
    seenClasses: MutableSet<Class<*>>,
    action: (T) -> Unit
) {
    // iterProducer is a lambda, not an already-evaluated Iterator<T>, specifically so the
    // ServiceLoader.load(...).iterator() call itself happens *inside* this try - on the JVM that
    // call is a guaranteed no-throw lazy constructor (all real work is deferred to hasNext()/next(),
    // already guarded below), but Android's core-library-desugared ServiceLoader backport
    // (desugar_jdk_libs, needed below the API level where the two-arg load(Class, ClassLoader)
    // overload is natively available) has a real bug: it can throw ServiceConfigurationError,
    // wrapping a NoSuchMethodError on ClassLoader.getClassLoadingLock, synchronously from
    // .iterator() itself. Since this whole function is inlined into some file's top-level `val`
    // initializer (e.g. provider's _baseContinuationAccessorProvider), an uncaught throw here blows
    // up that file's <clinit> - and once a class fails to initialize, the JVM permanently marks it
    // unusable, so every other class that references it afterward also fails with
    // NoClassDefFoundError, not just this one lookup. Confirmed by temporarily removing this guard
    // and running the Android connectedAndroidTest suite: a single ServiceConfigurationError here
    // cascaded into ~30 unrelated test failures.
    val iter = try {
        iterProducer()
    } catch (_: Throwable) {
        return
    }
    while (true) {
        try {
            if (!iter.hasNext()) {
                break
            }
            val instance = iter.next()
            if (seenClasses.add(instance.javaClass)) {
                action(instance)
            }
        } catch (_: Throwable) { }
    }
}

// Unchanged call-site shape for every existing loadService<T>() caller - only the import moves.
inline fun <reified T: Any> loadService(): T? {
    loadServices<T> { return it }
    return null
}
