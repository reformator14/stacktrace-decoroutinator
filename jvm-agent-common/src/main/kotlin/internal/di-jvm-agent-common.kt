@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.stacktracedecoroutinator.runtimesettings.internal.getRuntimeSettingsValue

internal val isBaseContinuationRedefinitionAllowed =
    getRuntimeSettingsValue({ isBaseContinuationRedefinitionAllowed }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isBaseContinuationRedefinitionAllowed",
            "true"
        ).toBoolean()
    }

internal val isRedefinitionAllowed =
    getRuntimeSettingsValue({ isRedefinitionAllowed }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isRedefinitionAllowed",
            "false"
        ).toBoolean()
    }
