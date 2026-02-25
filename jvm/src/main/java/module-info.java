module dev.reformator.stacktracedecoroutinator.jvm {
    requires dev.reformator.stacktracedecoroutinator.jvmagentcommon;
    requires dev.reformator.stacktracedecoroutinator.common;
    requires dev.reformator.stacktracedecoroutinator.provider;
    requires net.bytebuddy.agent;
    requires java.instrument;
    requires kotlin.stdlib;

    exports dev.reformator.stacktracedecoroutinator.jvm;
}
