package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.provider.internal.enabled
import dev.reformator.stacktracedecoroutinator.provider.internal.tailCallDeoptimize
import dev.reformator.stacktracedecoroutinator.runtimesettings.internal.getRuntimeSettingsValue

@Suppress("ObjectPropertyName")
private val _stacktraceElementsFactory: StacktraceElementsFactory? =
    if (enabled) StacktraceElementsFactoryImpl() else null

internal val stacktraceElementsFactory: StacktraceElementsFactory
    get() = _stacktraceElementsFactory!!

internal val recoveryExplicitStacktrace =
    enabled && getRuntimeSettingsValue({ it.recoveryExplicitStacktrace }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace",
            "true"
        ).toBoolean()
    }

internal val recoveryExplicitStacktraceTimeoutMs =
    (
        if (tailCallDeoptimize) {
            getRuntimeSettingsValue({ it.recoveryExplicitStacktraceTimeoutMs }) {
                System.getProperty(
                    "dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktraceTimeoutMs",
                    "500"
                ).toInt()
            }
        } else {
            0
        }
    ).toUInt()
