module dev.reformator.stacktracedecoroutinator.tests.nokotlinstdlib {
    // kotlinc needs this to compile (every Kotlin class carries a structural @kotlin.Metadata
    // annotation reference) - RemoveKotlinStdlibProcessor strips it from the compiled
    // module-info.class afterward, same as provider/runtime-settings do.
    requires kotlin.stdlib;

    requires org.junit.jupiter.api;

    requires static dev.reformator.bytecodeprocessor.intrinsics;

    exports dev.reformator.stacktracedecoroutinator.tests.nokotlinstdlib;
}
