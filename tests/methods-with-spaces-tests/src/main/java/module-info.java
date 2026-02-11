module dev.reformator.stacktracedecoroutinator.methodswithspacestests {
    requires kotlinx.coroutines.core;
    requires org.junit.jupiter.api;
    requires dev.reformator.stacktracedecoroutinator.tests;

    requires static dev.reformator.bytecodeprocessor.intrinsics;

    exports dev.reformator.stacktracedecoroutinator.methodswithspacestests;

    opens dev.reformator.stacktracedecoroutinator.methodswithspacestests;
}
