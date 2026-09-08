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
    processServiceIterator(ServiceLoader.load(type).iterator(), seenClasses, action)
    processServiceIterator(ServiceLoader.load(type, type.classLoader).iterator(), seenClasses, action)
}

@PublishedApi
internal inline fun <T: Any> processServiceIterator(
    iter: Iterator<T>,
    seenClasses: MutableSet<Class<*>>,
    action: (T) -> Unit
) {
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
