@file:Suppress("PackageDirectoryMismatch")
@file:AndroidKeep

package dev.reformator.stacktracedecoroutinator.mhinvoker.internal

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.provider.internal.specMethodType
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidKeep
import dev.reformator.stacktracedecoroutinator.provider.internal.CHECKSUM_VALID
import dev.reformator.stacktracedecoroutinator.provider.internal.DEPTH_VALID
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

internal val unknownSpecClass: Class<*>
    @GetOwnerClass get() = fail()

@Suppress("unused")
private fun unknown(spec: DecoroutinatorSpec, result: Any?, resumeChecksum: Int, depthChecksum: Int): Any? {
    var updatedDepthChecksum = depthChecksum - 1
    if (updatedDepthChecksum < DEPTH_VALID) {
        return spec.`$decoroutinator$checksumFailedMarker`
    }

    val resumeCookie = spec.`$decoroutinator$resumeCookie`
    var updatedResumeChecksum = spec.`$decoroutinator$getResumeChecksum`(resumeChecksum, resumeCookie)

    val nextSpecHandle = spec.`$decoroutinator$nextSpecHandle`
    val nextSpec = spec.`$decoroutinator$nextSpec`
    var updatedResult = result

    if (nextSpecHandle != null && nextSpec != null) {
        updatedResult = nextSpecHandle.invokeExact(nextSpec, result, updatedResumeChecksum, updatedDepthChecksum)
        updatedResumeChecksum = CHECKSUM_VALID
        updatedDepthChecksum = DEPTH_VALID
    }

    return spec.`$decoroutinator$resume`(updatedResult, updatedResumeChecksum, updatedDepthChecksum, resumeCookie)
}

internal fun getUnknownSpecMethodHandle(): MethodHandle =
    MethodHandles.lookup().findStatic(
        unknownSpecClass,
        ::unknown.name,
        specMethodType
    )
