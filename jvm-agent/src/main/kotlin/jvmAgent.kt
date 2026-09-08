@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorAgentKt")

package dev.reformator.stacktracedecoroutinator.jvmagent

import dev.reformator.bytecodeprocessor.intrinsics.ownerClass
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.addDecoroutinatorTransformer
import dev.reformator.stacktracedecoroutinator.runtimesettings.internal.getRuntimeSettingsValue
import java.io.File
import java.lang.instrument.Instrumentation
import java.util.jar.JarFile

// Some environments (e.g. attaching this agent statically to the Gradle daemon's own JVM via
// org.gradle.jvmargs) end up loading this very class through the bootstrap classloader instead of
// the system one, which breaks DispatchingProvider's classloader-based dispatch once other classes
// from this same (shaded, self-contained) jar get resolved through a different classloader further
// down. Explicitly appending this jar to the bootstrap search path - before addDecoroutinatorTransformer
// touches anything else from it - makes every class this agent needs resolve consistently through the
// bootstrap loader from here on, regardless of how this class itself got loaded. On by default: it only
// ever expands what the bootstrap loader can resolve, so it's harmless in every environment this agent
// is known to run in - but a settable escape hatch exists in case some environment this wasn't tested
// against reacts badly to it.
private val appendJarToBootstrapClassLoaderSearch: Boolean =
    getRuntimeSettingsValue({ it.appendJvmAgentJarToBootstrapClassLoaderSearch }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.appendJvmAgentJarToBootstrapClassLoaderSearch",
            "true"
        ).toBoolean()
    }

fun premain(@Suppress("UNUSED_PARAMETER") args: String?, inst: Instrumentation) {
    if (appendJarToBootstrapClassLoaderSearch) {
        // The JarFile must not be closed - the JVM keeps it open and keeps reading from it for the life
        // of the process.
        val agentJarFile = File(ownerClass.protectionDomain.codeSource.location.toURI())
        inst.appendToBootstrapClassLoaderSearch(JarFile(agentJarFile))
    }
    addDecoroutinatorTransformer(inst)
}
