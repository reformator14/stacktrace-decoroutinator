import dev.reformator.stacktracedecoroutinator.latesttests.jvm.wrapcontinuationtests.InstallDecoroutinatorLauncherSessionListener;
import org.junit.platform.launcher.LauncherSessionListener;

module dev.reformator.stacktracedecoroutinator.latesttests.jvm.wrapcontinuationtests {
    requires static dev.reformator.bytecodeprocessor.intrinsics;

    requires kotlin.stdlib;
    requires kotlinx.coroutines.core;
    requires dev.reformator.stacktracedecoroutinator.latesttests.tests;
    requires dev.reformator.stacktracedecoroutinator.latesttests.methodswithspacestests;
    requires dev.reformator.stacktracedecoroutinator.jvm;
    requires org.junit.platform.launcher;
    requires org.junit.jupiter.api;
    requires kotlin.test;

    provides LauncherSessionListener with InstallDecoroutinatorLauncherSessionListener;

    exports dev.reformator.stacktracedecoroutinator.latesttests.jvm.wrapcontinuationtests;
}
