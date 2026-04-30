package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.bytecodeprocessor.intrinsics.ownerClass
import java.io.InputStream

internal fun ClassLoader?.getResourceAsStream(name: String): InputStream? =
    this?.getResourceAsStream(name) ?: try {
        Thread.currentThread().contextClassLoader.getResourceAsStream(name)
    } catch (_: Throwable) {
        null
    } ?: try {
        ownerClass.classLoader.getResourceAsStream(name)
    } catch (_: Throwable) {
        null
    } ?: try {
        ClassLoader.getSystemClassLoader().getResourceAsStream(name)
    } catch (_: Throwable) {
        null
    } ?: try {
        ClassLoader.getSystemResourceAsStream(name)
    } catch (_: Throwable) {
        null
    }
