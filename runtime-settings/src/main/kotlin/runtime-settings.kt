@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtimesettings

import dev.reformator.stacktracedecoroutinator.runtimesettings.internal.defaultValue

interface DecoroutinatorRuntimeSettingsProvider {
    //Common settings

    val enabled: Boolean
        get() = defaultValue()

    val recoveryExplicitStacktrace: Boolean
        get() = defaultValue()

    val recoveryExplicitStacktraceTimeoutMs: UInt
        get() = defaultValue()

    val tailCallDeoptimize: Boolean
        get() = defaultValue()

    val methodsNumberThreshold: Int
        get() = defaultValue()

    val fillUnknownElementsWithClassName: Boolean
        get() = defaultValue()

    val isUsingElementFactoryForBaseContinuationEnabled: Boolean
        get() = defaultValue()

    val isUsingElementCacheForManualContinuationGetElementMethodEnabled: Boolean
        get() = defaultValue()

    // JVM Agent settings

    val isBaseContinuationRedefinitionAllowed: Boolean
        get() = defaultValue()

    val isRedefinitionAllowed: Boolean
        get() = defaultValue()

    // Embedded Debug Probes settings

    val enableCreationStackTraces: Boolean
        get() = defaultValue()

    val installDebugProbes: Boolean
        get() = defaultValue()

    // Generator Android settings

    val androidGeneratorAttemptsCount: Int
        get() = defaultValue()

    // end

    val priority: Int
        get() = 0
}
