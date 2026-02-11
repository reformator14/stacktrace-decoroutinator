module dev.reformator.stacktracedecoroutinator.specmethodbuilder {
    requires static dev.reformator.stacktracedecoroutinator.intrinsics;

    requires kotlin.stdlib;
    requires org.objectweb.asm.tree;
    requires dev.reformator.stacktracedecoroutinator.provider;

    exports dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal to
            dev.reformator.stacktracedecoroutinator.generatorjvm,
            dev.reformator.stacktracedecoroutinator.generator.tests,
            dev.reformator.stacktracedecoroutinator.classtransformer;
}
