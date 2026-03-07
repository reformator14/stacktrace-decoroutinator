import dev.reformator.stacktracedecoroutinator.latesttests.jvm.dynamicagenttests.InstallDecoroutinatorLauncherSessionListener;
import org.junit.platform.launcher.LauncherSessionListener;

module dev.reformator.stacktracedecoroutinator.latesttests.jvm.dynamicagenttests {
    requires kotlin.stdlib;
    requires dev.reformator.stacktracedecoroutinator.latesttests.tests;
    requires dev.reformator.stacktracedecoroutinator.latesttests.methodswithspacestests;
    requires dev.reformator.stacktracedecoroutinator.jvm;
    requires org.junit.platform.launcher;

    provides LauncherSessionListener with InstallDecoroutinatorLauncherSessionListener;

    exports dev.reformator.stacktracedecoroutinator.latesttests.jvm.dynamicagenttests;
}
