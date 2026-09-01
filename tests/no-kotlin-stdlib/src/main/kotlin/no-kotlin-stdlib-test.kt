@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.tests.nokotlinstdlib

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.net.URLClassLoader

// This module deliberately has no real kotlin-stdlib dependency anywhere on its own graph (see
// gradle.properties + RemoveKotlinStdlibProcessor in this module's build.gradle.kts) - the
// jvm-agent:tests-no-kotlin-stdlib and jvm-agent:jdk8-tests-no-kotlin-stdlib modules depend on it
// and add nothing else, so the JVM these tests actually run in has zero real kotlin-stdlib on its
// own classpath. That is what makes DispatchingProvider's own hasKotlinStdlibVisible(defaultLoader)
// check (jvm-agent/src/main/kotlin/internal/dispatching-provider.kt) genuinely, naturally return
// false - this test exercises the real detection path, not the forceAgentClassLoaderDispatchingProvider
// escape hatch.
//
// Because of the above, this file's own code must not pull in any real (non-inline) kotlin-stdlib
// call either - keep it to plain java.* APIs, no lambdas/higher-order functions, no kotlin
// collection-literal calls, no `by lazy` (a real, non-inline kotlin-stdlib call). See
// provider/src/main/kotlin/internal/di-provider.kt for the same style already working under
// RemoveKotlinStdlibProcessor in this codebase.
open class NoKotlinStdlibTest {
    @Test
    fun basic() {
        checkStackRecovery(stubClassLoader, false)
    }

    @Test
    fun tailCallOptimized() {
        checkStackRecovery(stubClassLoader, true)
    }

    // Regression test for a real bug found while writing this test module: provider-api-internal.kt
    // used to cache the BaseContinuationAccessor in a single process-wide var, built (via
    // MethodHandles) against whichever BaseContinuationImpl class it saw first. A second,
    // independently-built class loader - even loading byte-identical kotlin-stdlib content - gets
    // its own, distinct BaseContinuationImpl Class object (JVM class identity is (defining class
    // loader, name), not bytecode content), and invoking the stale cached accessor's handles
    // against it threw ClassCastException deep inside coroutine machinery - surfacing as a hang
    // under runBlocking, since kotlinx.coroutines treats it as a fatal internal error rather than
    // propagating it. DecoroutinatorProvider.getBaseContinuationAccessor/prepareBaseContinuationAccessor
    // are now delegated per target class loader (DispatchingProvider routes them the same way as
    // every other member, via a per-class-loader common.internal.Provider instance - see
    // dispatching-provider.kt), so two independently-built class loaders must both work correctly
    // in the same process. Timeout is a safety net in case this ever regresses: a hang (rather
    // than a clean failure) is exactly what this bug looked like originally.
    @Test
    @Timeout(30)
    fun independentClassLoaders() {
        checkStackRecovery(buildStubClassLoader(), false)
        checkStackRecovery(buildStubClassLoader(), true)
    }
}

private fun checkStackRecovery(loader: URLClassLoader, allowTailCallOptimization: Boolean) {
    val stubClass = loader.loadClass("dev.reformator.stacktracedecoroutinator.test.ClassWithSuspendFunctionsStub")
    val instance = stubClass.getDeclaredConstructor().newInstance()
    val performCheck = stubClass.getDeclaredMethod("performCheck", java.lang.Boolean.TYPE)
    performCheck.invoke(instance, allowTailCallOptimization)
}

// Parent must be the system class loader, not null: the agent's ClassFileTransformer only
// transforms a class if it can resolve provider's classes through that class's own loader
// (hasProviderApiDependency in class-transformer-jvm-agent-common.kt), which requires delegating
// up to the system class loader (where -javaagent put the shaded agent jar). kotlinStdlibJarPath
// is listed first so it, not custom-loader's own bundled copy, is what resolves kotlin.* here - a
// real, standalone kotlin-stdlib copy, distinct from whatever (if anything) the agent's own loader
// can see.
private fun buildStubClassLoader(): URLClassLoader = URLClassLoader(
    arrayOf(File(kotlinStdlibJarPath).toURI().toURL(), File(customLoaderJarPath).toURI().toURL()),
    ClassLoader.getSystemClassLoader()
)

private val stubClassLoader = buildStubClassLoader()

private val customLoaderJarPath: String
    @LoadConstant("customLoaderJarPath") get() { fail() }

private val kotlinStdlibJarPath: String
    @LoadConstant("kotlinStdlibJarPath") get() { fail() }
