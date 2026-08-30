@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.stacktracedecoroutinator.runtimesettings.internal.getRuntimeSettingsValue

internal val isRedefinitionChangingClassLayoutAllowed =
    getRuntimeSettingsValue({ it.isRedefinitionChangingClassLayoutAllowed }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isRedefinitionChangingClassLayoutAllowed",
            "false"
        ).toBoolean()
    }
