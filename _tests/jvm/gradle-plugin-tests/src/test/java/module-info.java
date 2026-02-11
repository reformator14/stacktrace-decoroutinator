module dev.reformator.stacktracedecoroutinator.tests.jvm.gradleplugintests {
    requires static dev.reformator.bytecodeprocessor.intrinsics;

    requires dev.reformator.stacktracedecoroutinator.tests;
    requires dev.reformator.stacktracedecoroutinator.methodswithspacestests;
    //noinspection JavaModuleDefinition
    requires kotlinx.coroutines.core;
    requires org.junit.jupiter.api;
    requires org.jetbrains.annotations;
    requires kotlin.test.junit5;

    exports dev.reformator.stacktracedecoroutinator.tests.jvm.gradleplugintests;
}
