@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package dev.reformator.stacktracedecoroutinator.tests.android.wronggeneratortests

import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorCommonApi
import dev.reformator.stacktracedecoroutinator.provider.internal.methodHandleInvoker
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.lang.reflect.InvocationTargetException

class AndroidWrongGeneratorTests {
    @Test
    fun checkStatus() {
        val status = DecoroutinatorCommonApi.getStatus { it() }
        assertFalse(status.successful, status.description)
    }

    @Test
    fun unknownSpecMethodPresentInRecoveredTrace() = runBlocking {
        suspend fun topMethod(): Array<StackTraceElement> {
            yield()
            return Thread.currentThread().stackTrace
        }
        assertTrue(topMethod().any { it.className == methodHandleInvoker.unknownSpecMethodClass.name })
    }

    @Test
    fun androidGeneratorIsInClasspath() {
        val generatorConstructor =
            Class.forName(
                "dev.reformator.stacktracedecoroutinator.generatorjvm.internal.GeneratorJvmSpecMethodsFactory"
            ).getDeclaredConstructor()
        try {
            generatorConstructor.newInstance()
        } catch (_: InvocationTargetException) {
            return
        }
        fail<Unit>()
    }
}