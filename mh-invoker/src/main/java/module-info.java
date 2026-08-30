import dev.reformator.stacktracedecoroutinator.provider.internal.MethodHandleInvoker;
import dev.reformator.stacktracedecoroutinator.provider.internal.VarHandleInvoker;
import dev.reformator.stacktracedecoroutinator.mhinvoker.internal.RegularMethodHandleInvoker;
import dev.reformator.stacktracedecoroutinator.mhinvoker.internal.RegularVarHandleInvoker;

module dev.reformator.stacktracedecoroutinator.mhinvoker {
    requires static dev.reformator.bytecodeprocessor.intrinsics;

    requires dev.reformator.stacktracedecoroutinator.provider;
    requires dev.reformator.stacktracedecoroutinator.common;

    provides MethodHandleInvoker with RegularMethodHandleInvoker;
    provides VarHandleInvoker with RegularVarHandleInvoker;
}
