import dev.reformator.stacktracedecoroutinator.naivebasecontinuationaccessor.NaiveBaseContinuationAccessorProvider;
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider;

module dev.reformator.stacktracedecoroutinator.naivebasecontinuationaccessor {
    requires static dev.reformator.stacktracedecoroutinator.intrinsics;

    requires dev.reformator.stacktracedecoroutinator.provider;
    requires kotlin.stdlib;

    exports dev.reformator.stacktracedecoroutinator.naivebasecontinuationaccessor;

    provides BaseContinuationAccessorProvider with NaiveBaseContinuationAccessorProvider;
}
