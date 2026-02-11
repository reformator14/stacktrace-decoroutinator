package dev.reformator.stacktracedecoroutinator.tests.jvm.gradleplugintests;

import kotlinx.coroutines.debug.internal.DebugProbesImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DebugProbesAccessor {
    public static @NotNull List<?> dumpCoroutinesInfo() {
        return DebugProbesImpl.INSTANCE.dumpCoroutinesInfo();
    }
}
