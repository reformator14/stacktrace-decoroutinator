module dev.reformator.stacktracedecoroutinator.tests {
    requires static dev.reformator.stacktracedecoroutinator.common;

    requires kotlinx.coroutines.core;
    requires org.junit.jupiter.api;
    requires junit;
    requires dev.reformator.stacktracedecoroutinator.tests.duplicateentityjar;

    requires static dev.reformator.bytecodeprocessor.intrinsics;

    exports dev.reformator.stacktracedecoroutinator.tests;

    opens dev.reformator.stacktracedecoroutinator.tests;
}
