import dev.reformator.stacktracedecoroutinator.common.internal.*;
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider;
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider;

module dev.reformator.stacktracedecoroutinator.common {
    requires static dev.reformator.bytecodeprocessor.intrinsics;
    requires static dev.reformator.stacktracedecoroutinator.intrinsics;

    requires kotlin.stdlib;
    requires dev.reformator.stacktracedecoroutinator.provider;
    requires dev.reformator.stacktracedecoroutinator.runtimesettings;

    exports dev.reformator.stacktracedecoroutinator.common;
    exports dev.reformator.stacktracedecoroutinator.common.internal to
            dev.reformator.stacktracedecoroutinator.generatorjvm,
            dev.reformator.stacktracedecoroutinator.jvmagentcommon,
            dev.reformator.stacktracedecoroutinator.jvm,
            dev.reformator.stacktracedecoroutinator.generator.tests,
            dev.reformator.stacktracedecoroutinator.jvm.tests,
            dev.reformator.stacktracedecoroutinator.mhinvoker,
            dev.reformator.stacktracedecoroutinator.mhinvokerjvm;

    provides DecoroutinatorProvider with Provider;

    uses SpecMethodsFactory;
    uses AnnotationMetadataResolver;
    uses MethodHandleInvoker;
    uses VarHandleInvoker;
    uses BaseContinuationAccessorProvider;
}
