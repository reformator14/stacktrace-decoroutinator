import dev.reformator.stacktracedecoroutinator.runtimesettings.DecoroutinatorRuntimeSettingsProvider;

module dev.reformator.stacktracedecoroutinator.runtimesettings {
    requires static kotlin.stdlib;

    exports dev.reformator.stacktracedecoroutinator.runtimesettings;
    exports dev.reformator.stacktracedecoroutinator.runtimesettings.internal to
            dev.reformator.stacktracedecoroutinator.common,
            dev.reformator.stacktracedecoroutinator.provider,
            dev.reformator.stacktracedecoroutinator.jvmagentcommon,
            kotlinx.coroutines.core;

    uses DecoroutinatorRuntimeSettingsProvider;
}
