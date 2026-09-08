@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtimesettings.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.loadServices
import dev.reformator.stacktracedecoroutinator.runtimesettings.DecoroutinatorRuntimeSettingsProvider
import java.util.Collections
import java.util.Comparator
import java.util.function.Function

@Suppress("ObjectInheritsException", "JavaIoSerializableObjectMustHaveReadResolve")
private object DefaultValueException: Exception()

internal fun defaultValue(): Nothing =
    throw DefaultValueException

sealed interface RuntimeSettingsValue<out T> {
    object Default: RuntimeSettingsValue<Nothing>
    class Value<out T>(val value: T): RuntimeSettingsValue<T>
}

private class RuntimeSettingsProviderWithPriority(
    val provider: DecoroutinatorRuntimeSettingsProvider,
    val priority: Int
)

// loadServices (intrinsics module) searches both the thread's context classloader and
// DecoroutinatorRuntimeSettingsProvider::class.java's own classloader, and calls back once per distinct
// implementation found across both - every found provider is needed here (unlike a single-result
// loadService<T>()) since same-priority providers disagreeing on a value must be detected as a conflict
// below, not silently reduced to just the first one found.
private val runtimeSettingsProviderInstances: List<RuntimeSettingsProviderWithPriority> = run {
    val list = ArrayList<RuntimeSettingsProviderWithPriority>()
    loadServices<DecoroutinatorRuntimeSettingsProvider> { provider ->
        list.add(RuntimeSettingsProviderWithPriority(provider, provider.priority))
    }
    Collections.sort(list, Comparator { a, b -> b.priority - a.priority })
    list
}

fun <T> getRuntimeSettingsValue(get: Function<DecoroutinatorRuntimeSettingsProvider, T>): RuntimeSettingsValue<T> {
    var index = 0
    val value = run {
        while (index < runtimeSettingsProviderInstances.size) {
            try {
                return@run get.apply(runtimeSettingsProviderInstances[index].provider)
            } catch (_: DefaultValueException) {
                index++
            }
        }
        return RuntimeSettingsValue.Default
    }
    val provider = runtimeSettingsProviderInstances[index].provider
    val priority = runtimeSettingsProviderInstances[index].priority
    index++
    while (index < runtimeSettingsProviderInstances.size && runtimeSettingsProviderInstances[index].priority == priority) {
        try {
            val otherProvider = runtimeSettingsProviderInstances[index].provider
            val otherValue = get.apply(otherProvider)
            if (otherValue != value) {
                error("different values with the same priority[$priority]: [$value] from [$provider] and [$otherValue] from [$otherProvider]")
            }
        }  catch (_: DefaultValueException) { }
        index++
    }
    return RuntimeSettingsValue.Value(value)
}

inline fun <T> getRuntimeSettingsValue(
    get: Function<DecoroutinatorRuntimeSettingsProvider, T>,
    default: () -> T
): T =
    when(val value = getRuntimeSettingsValue(get)) {
        is RuntimeSettingsValue.Value<T> -> value.value
        RuntimeSettingsValue.Default -> default()
    }
