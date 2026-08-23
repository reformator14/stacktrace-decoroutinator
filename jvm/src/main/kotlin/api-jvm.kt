@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvm

import dev.reformator.stacktracedecoroutinator.intrinsics.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.jvm.internal.isTransformed
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.addDecoroutinatorTransformer
import net.bytebuddy.agent.ByteBuddyAgent
import java.lang.instrument.Instrumentation
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DecoroutinatorJvmApi {
    @Volatile private var instrumentation: Instrumentation? = null

    fun install() {
        lock.withLock {
            if (!initialized) {
                val inst = ByteBuddyAgent.install()
                addDecoroutinatorTransformer(inst)
                instrumentation = inst
                initialized = true
            }
        }
        val baseContinuation = Class.forName(BASE_CONTINUATION_CLASS_NAME)
        if (!baseContinuation.isTransformed) {
            ByteBuddyAgent.install().retransformClasses(baseContinuation)
        }
        if (!baseContinuation.isTransformed) {
            error("Cannot install Decoroutinator runtime " +
                    "because class [$BASE_CONTINUATION_CLASS_NAME] is already loaded " +
                    "and class retransformations is not allowed.")
        }
    }

    fun retransformClass(clazz: Class<*>) {
        instrumentation.let { inst ->
            if (inst == null) {
                error("Decoroutinator is not installed. Please call ${DecoroutinatorJvmApi::class.java.name}#${::install.name}()")
            }
            inst.retransformClasses(clazz)
        }
    }

    private val lock = ReentrantLock()
    private var initialized = false
}
