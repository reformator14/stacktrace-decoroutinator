@file:Suppress("PackageDirectoryMismatch")
@file:AndroidKeep

package dcunknown

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.internal.specMethodType
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidKeep
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

internal val unknownSpecClass: Class<*>
    @GetOwnerClass get() = fail()

@Suppress("unused")
private fun unknown(spec: DecoroutinatorSpec, result: Any?): Any? {
    val updatedResult = if (!spec.`$decoroutinator$isLastSpec`()) {
        spec.`$decoroutinator$getNextSpecHandle`().invokeExact(
            spec.`$decoroutinator$getNextSpec`(),
            result
        )
    } else {
        result
    }
    return spec.`$decoroutinator$resumeNext`(updatedResult)
}

internal fun getUnknownSpecMethodHandle(): MethodHandle =
    MethodHandles.lookup().findStatic(
        unknownSpecClass,
        ::unknown.name,
        specMethodType
    )
