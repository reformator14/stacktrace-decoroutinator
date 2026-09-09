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
            APPEND_TO_BOOTSTRAP_PROPERTY_NAME,
            "true"
        ).toBoolean()
    }

private const val APPEND_TO_BOOTSTRAP_PROPERTY_NAME =
    "dev.reformator.stacktracedecoroutinator.appendJvmAgentJarToBootstrapClassLoaderSearch"

fun premain(@Suppress("UNUSED_PARAMETER") args: String?, inst: Instrumentation) {
    if (appendJarToBootstrapClassLoaderSearch) {
        try {
            // The JarFile must not be closed - the JVM keeps it open and keeps reading from it for the life
            // of the process.
            val agentJarFile = File(ownerClass.protectionDomain.codeSource.location.toURI())
            inst.appendToBootstrapClassLoaderSearch(JarFile(agentJarFile))
        } catch (e: Throwable) {
            // codeSource is null (NPE right here) when this agent is attached to IntelliJ IDEA's own
            // process itself - e.g. added straight to IDEA's own startup .vmoptions/launch script (so
            // every IDEA-launched JVM, IDEA's own included, runs with it), not just to a run/debug
            // configuration IDEA builds for the user's own code. Confirmed cause (checked against
            // IntelliJ IDEA 2026.2, other versions almost certainly the same): IDEA's own
            // product-info.json sets -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader
            // for every launch profile, so this agent's premain class - loaded via "the system class
            // loader" per the java.lang.instrument spec - is defined by IDEA's own PathClassLoader
            // (extends UrlClassLoader), not the JDK's AppClassLoader. Disassembling
            // platform-loader.jar's UrlClassLoader.class shows its defineClass(...) call passing an
            // explicit `aconst_null` as the ProtectionDomain argument to
            // ClassLoader.defineClass(String, byte[], int, int, ProtectionDomain) - not a computed
            // value, a deliberate null (IDEA's classloader never needs CodeSource-based permission
            // checks, since that machinery only ever mattered for the long-deprecated, now-removed
            // SecurityManager). The JDK synthesizes a default ProtectionDomain for a null one, and that
            // default's CodeSource is null - exactly what .location NPEs on here. This whole block is a
            // best-effort optimization for the Gradle-daemon-attachment scenario described above, not
            // something addDecoroutinatorTransformer depends on, so any failure here is caught and
            // logged rather than aborting agent startup.
            System.err.println(
                "Failed to add Decoroutinator agent jar to BootstrapClassLoaderSearch: ${e.message}.\n" +
                "Add '-D$APPEND_TO_BOOTSTRAP_PROPERTY_NAME=false' to disable this message."
            )
            e.printStackTrace()
        }
    }
    addDecoroutinatorTransformer(inst)
}
