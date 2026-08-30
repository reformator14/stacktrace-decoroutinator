import dev.reformator.stacktracedecoroutinator.provider.internal.AnnotationMetadataResolver;
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider;
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider;
import dev.reformator.stacktracedecoroutinator.provider.internal.MethodHandleInvoker;
import dev.reformator.stacktracedecoroutinator.provider.internal.SpecMethodsFactory;
import dev.reformator.stacktracedecoroutinator.provider.internal.VarHandleInvoker;

module dev.reformator.stacktracedecoroutinator.provider {
    requires static kotlin.stdlib;
    requires static dev.reformator.bytecodeprocessor.intrinsics;
    requires static dev.reformator.stacktracedecoroutinator.intrinsics;

    requires dev.reformator.stacktracedecoroutinator.runtimesettings;

    exports dev.reformator.stacktracedecoroutinator.provider;
    exports dev.reformator.stacktracedecoroutinator.provider.internal to
            kotlin.stdlib,
            dev.reformator.stacktracedecoroutinator.common,
            dev.reformator.stacktracedecoroutinator.mhinvoker,
            dev.reformator.stacktracedecoroutinator.mhinvokerjvm,
            dev.reformator.stacktracedecoroutinator.generatorjvm,
            dev.reformator.stacktracedecoroutinator.jvmagentcommon,
            dev.reformator.stacktracedecoroutinator.generator.tests,
            dev.reformator.stacktracedecoroutinator.specmethodbuilder,
            dev.reformator.stacktracedecoroutinator.classtransformer,
            dev.reformator.stacktracedecoroutinator.naivebasecontinuationaccessor;

    uses DecoroutinatorProvider;
    uses SpecMethodsFactory;
    uses AnnotationMetadataResolver;
    uses MethodHandleInvoker;
    uses VarHandleInvoker;
    uses BaseContinuationAccessorProvider;
}
