import dev.reformator.stacktracedecoroutinator.jvm.tests.InstallDecoroutinatorLauncherSessionListener;
import org.junit.platform.launcher.LauncherSessionListener;

module dev.reformator.stacktracedecoroutinator.jvm.tests {
    requires static dev.reformator.stacktracedecoroutinator.intrinsics;

    requires dev.reformator.stacktracedecoroutinator.tests;
    requires dev.reformator.stacktracedecoroutinator.methodswithspacestests;
    requires dev.reformator.stacktracedecoroutinator.jvm;
    requires kotlin.test.junit5;
    requires org.junit.platform.launcher;

    provides LauncherSessionListener with InstallDecoroutinatorLauncherSessionListener;

    exports dev.reformator.stacktracedecoroutinator.jvm.tests;
}
