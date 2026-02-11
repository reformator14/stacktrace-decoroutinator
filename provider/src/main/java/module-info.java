import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider;

module dev.reformator.stacktracedecoroutinator.provider {
    requires static kotlin.stdlib;
    requires static dev.reformator.bytecodeprocessor.intrinsics;

    exports dev.reformator.stacktracedecoroutinator.provider;
    exports dev.reformator.stacktracedecoroutinator.provider.internal to
            kotlin.stdlib,
            dev.reformator.stacktracedecoroutinator.common,
            dev.reformator.stacktracedecoroutinator.mhinvoker,
            dev.reformator.stacktracedecoroutinator.generatorjvm,
            dev.reformator.stacktracedecoroutinator.jvmagentcommon,
            dev.reformator.stacktracedecoroutinator.generator.tests,
            dev.reformator.stacktracedecoroutinator.specmethodbuilder,
            dev.reformator.stacktracedecoroutinator.classtransformer,
            dev.reformator.stacktracedecoroutinator.naivebasecontinuationaccessor;

    uses DecoroutinatorProvider;
}
