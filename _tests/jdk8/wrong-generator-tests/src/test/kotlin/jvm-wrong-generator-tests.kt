@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package dev.reformator.stacktracedecoroutinator.tests.jdk8.wronggeneratortests

import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorCommonApi
import dev.reformator.stacktracedecoroutinator.provider.internal.methodHandleInvoker
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class JvmWrongGeneratorTests {
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
        Class.forName("dalvik.system.InMemoryDexClassLoader")
        val generatorConstructor =
            Class.forName(
                "dev.reformator.stacktracedecoroutinator.generatorandroid.AndroidSpecMethodsFactory"
            ).getDeclaredConstructor()
        try {
            generatorConstructor.newInstance()
        } catch (e: InvocationTargetException) {
            assertTrue(e.cause!!.message!!.contains("Stub!"))
            return
        }
        fail()
    }
}
