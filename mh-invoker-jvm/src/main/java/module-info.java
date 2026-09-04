import dev.reformator.stacktracedecoroutinator.provider.internal.MethodHandleInvoker;
import dev.reformator.stacktracedecoroutinator.provider.internal.VarHandleInvoker;
import dev.reformator.stacktracedecoroutinator.mhinvokerjvm.internal.JvmMethodHandleInvoker;
import dev.reformator.stacktracedecoroutinator.mhinvokerjvm.internal.JvmVarHandleInvoker;

module dev.reformator.stacktracedecoroutinator.mhinvokerjvm {
    requires static dev.reformator.bytecodeprocessor.intrinsics;
    requires static dev.reformator.stacktracedecoroutinator.intrinsics;

    requires dev.reformator.stacktracedecoroutinator.provider;
    requires kotlin.stdlib;

    provides MethodHandleInvoker with JvmMethodHandleInvoker;
    provides VarHandleInvoker with JvmVarHandleInvoker;
}
