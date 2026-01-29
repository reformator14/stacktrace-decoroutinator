[![Maven Central](https://img.shields.io/maven-central/v/dev.reformator.stacktracedecoroutinator/stacktrace-decoroutinator-common 'JVM artifact')](https://central.sonatype.com/artifact/dev.reformator.stacktracedecoroutinator/stacktrace-decoroutinator-jvm)
[![Gradle Plugin Portal Version](https://img.shields.io/gradle-plugin-portal/v/dev.reformator.stacktracedecoroutinator 'Gradle plugin')](https://plugins.gradle.org/plugin/dev.reformator.stacktracedecoroutinator)
# Stacktrace-decoroutinator
Library for recovering stack trace in exceptions thrown in Kotlin coroutines.

Supports JVM 1.8 or higher and Android API 14 or higher.

### Motivation
Coroutines is a significant Kotlin feature that allows you to write asynchronous code in synchronous style.

It's absolutely perfect until you need to investigate problems in your code.

One of the common problems is the shortened stack trace in exceptions thrown in coroutines. For example, this code prints out the stack trace below:
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

suspend fun fun1() {
    delay(10)
    throw Exception("exception at ${System.currentTimeMillis()}")
}

suspend fun fun2() {
    fun1()
    delay(10)
}

suspend fun fun3() {
    fun2()
    delay(10)
}

fun main() {
    try {
        runBlocking {
            fun3()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```
```
java.lang.Exception: exception at 1641842199891
  at MainKt.fun1(main.kt:6)
  at MainKt$fun1$1.invokeSuspend(main.kt)
  at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
  at kotlinx.coroutines.DispatchedTaskKt.resume(DispatchedTask.kt:234)
  at kotlinx.coroutines.DispatchedTaskKt.dispatch(DispatchedTask.kt:166)
  at kotlinx.coroutines.CancellableContinuationImpl.dispatchResume(CancellableContinuationImpl.kt:397)
  at kotlinx.coroutines.CancellableContinuationImpl.resumeImpl(CancellableContinuationImpl.kt:431)
  at kotlinx.coroutines.CancellableContinuationImpl.resumeImpl$default(CancellableContinuationImpl.kt:420)
  at kotlinx.coroutines.CancellableContinuationImpl.resumeUndispatched(CancellableContinuationImpl.kt:518)
  at kotlinx.coroutines.EventLoopImplBase$DelayedResumeTask.run(EventLoop.common.kt:494)
  at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:279)
  at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:85)
  at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:59)
  at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source)
  at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking$default(Builders.kt:38)
  at kotlinx.coroutines.BuildersKt.runBlocking$default(Unknown Source)
  at MainKt.main(main.kt:21)
  at MainKt.main(main.kt)
```
The stack trace doesn't represent the true coroutine call stack: calls of functions `fun3` and `fun2` are absent.

In complex systems, even more calls may be missing. This can make debugging much more difficult.

Some examples of suffering from this problem:
- https://github.com/arrow-kt/arrow/issues/2647
- https://stackoverflow.com/questions/54349418/how-to-recover-the-coroutines-true-call-trace
- https://stackoverflow.com/questions/69226016/how-to-get-full-exception-stacktrace-when-using-await-on-completablefuture

The Kotlin team are known about the problem and has come up with a [solution](https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/debugging.md#stacktrace-recovery), but it solves just a part of the cases.
For example, the exception from the example above still lacks some calls.

### Solution
Decoroutinator replaces the coroutine awakening implementation.

It generates methods at runtime with names that match the entire coroutine call stack.

These methods don't do anything except call each other in the coroutine call stack order.

Thus, if the coroutine throws an exception, they mimic the real call stack of the coroutine during the creation of the exception stacktrace.

Check out [the Decoroutinator playground](https://decoroutinator.reformator.dev/playground/).

### JVM
There are three possible ways to enable Decoroutinator for a JVM.
1. If you build your project with Gradle, just apply the Gradle plugin with id `dev.reformator.stacktracedecoroutinator`.
2. Add `-javaagent:stacktrace-decoroutinator-jvm-agent-2.6.1.jar` to your JVM start arguments. The corresponding dependency is `dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-jvm-agent:2.6.1`.
3. Add the dependency `dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-jvm:2.6.1` and call method `DecoroutinatorJvmApi.install()`.

The first option generates auxiliary methods at build time, and the other two use the Java instrumentation API at runtime.

Usage example:
```kotlin
package dev.reformator.stacktracedecoroutinator.jvmtests

import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

object Test {
    suspend fun rec(depth: Int) {
        if (depth == 0) {
            yield()
            throw Exception("exception at ${System.currentTimeMillis()}")
        }
        rec(depth - 1)
    }
}

fun main() {
    DecoroutinatorJvmApi.install() // enable stacktrace-decoroutinator runtime
    try {
        runBlocking {
            Test.rec(10)
        }
    } catch (e: Exception) {
        e.printStackTrace() // print full stack trace with 10 recursive calls
    }
}
```
prints out:
```
java.lang.Exception: exception at 1764729227496
	at dev.reformator.stacktracedecoroutinator.jvmtests.Test.rec(example.kt:11)
	at dev.reformator.stacktracedecoroutinator.jvmtests.Test$rec$1.invokeSuspend(example.kt)
	at kotlin.coroutines.jvm.internal.DecoroutinatorBaseContinuationAccessorImpl.invokeSuspend(base-continuation-accessor.kt:15)
	at dev.reformator.stacktracedecoroutinator.common.internal.DecoroutinatorSpecImpl.resumeNext(utils-common.kt:247)
	at dev.reformator.stacktracedecoroutinator.jvmtests.Test.rec(example.kt:13)
	at dev.reformator.stacktracedecoroutinator.jvmtests.Test.rec(example.kt:13)
	at dev.reformator.stacktracedecoroutinator.jvmtests.Test.rec(example.kt:13)
	at dev.reformator.stacktracedecoroutinator.jvmtests.Test.rec(example.kt:13)
	at dev.reformator.stacktracedecoroutinator.jvmtests.Test.rec(example.kt:13)
	at dev.reformator.stacktracedecoroutinator.jvmtests.Test.rec(example.kt:13)
	at dev.reformator.stacktracedecoroutinator.jvmtests.Test.rec(example.kt:13)
	at dev.reformator.stacktracedecoroutinator.jvmtests.Test.rec(example.kt:13)
	at dev.reformator.stacktracedecoroutinator.jvmtests.Test.rec(example.kt:13)
	at dev.reformator.stacktracedecoroutinator.jvmtests.Test.rec(example.kt:13)
	at dev.reformator.stacktracedecoroutinator.jvmtests.ExampleKt$main$1.invokeSuspend(example.kt:21)
	at dev.reformator.stacktracedecoroutinator.mhinvoker.internal.RegularMethodHandleInvoker.callSpecMethod(mh-invoker.kt:20)
	at dev.reformator.stacktracedecoroutinator.common.internal.AwakenerKt.callSpecMethods(awakener.kt:225)
	at dev.reformator.stacktracedecoroutinator.common.internal.AwakenerKt.awake(awakener.kt:36)
	at dev.reformator.stacktracedecoroutinator.common.internal.Provider.awakeBaseContinuation(provider-impl.kt:38)
	at dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProviderInternalApiKt.awakeBaseContinuation(provider-api-internal.kt:20)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:104)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:277)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:95)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:69)
	at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking$default(Builders.kt:48)
	at kotlinx.coroutines.BuildersKt.runBlocking$default(Unknown Source)
	at dev.reformator.stacktracedecoroutinator.jvmtests.ExampleKt.main(example.kt:20)
	at dev.reformator.stacktracedecoroutinator.jvmtests.ExampleKt.main(example.kt)
```

### Android
For Android there is only one option to enable Stacktrace-decoroutinator - apply the Gradle plugin `dev.reformator.stacketracedecoroutinator` to your application's project.
```kotlin
plugins {
    id("dev.reformator.stacktracedecoroutinator") version "2.6.1"
}
```
Besides, Decoroutinator uses [MethodHandle API](https://developer.android.com/reference/java/lang/invoke/MethodHandle) which requires Android API level at least 26 (Android 8) so the stack trace recovery machinery doesn't work on Android less than 8.

### Embedding DebugProbes
Also, Decoroutinator allows to embed [DebugProbes](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-debug/kotlinx.coroutines.debug/-debug-probes/) into your Android application.
DebugProbes is a mechanism for dumping coroutine state and stack traces at runtime.
It can be useful for debugging purposes.
```kotlin
stacktraceDecoroutinator {
    embedDebugProbesForAndroid = true
    // or following line if you want to embed DebugProbes only for tests
    // embedDebugProbesForAndroidTest = true
}
```

### Using Decoroutinator Gradle plugin only for tests
If you want to use Decoroutinator for test only, it's recommended to separate your tests in a different Gradle subproject and apply Decoroutinator Gradle plugin to it.
But if you don't want to separate your tests, it's still possible by adding a configuration below to your `build.gradle.kts`:
```kotlin
stacktraceDecoroutinator {
    androidTestsOnly = true
}
```

### Using Decoroutinator Gradle plugin on Android with minification enabled
Please add the following ProGuard config file to your `build.gradle.kts` for Decoroutinator:
```kotlin
android {
    buildTypes {
        release {
            proguardFiles(decoroutinatorAndroidProGuardRules())
        }
    }
}
```

### Using Decoroutinator with Kotest
[Kotest](https://kotest.io) 6.0 offers decoroutinator support out of the box.
See documentation on how to integrate [here](https://kotest.io/docs/extensions/decoroutinator.html).

### Problem with Shadow Gradle plugin
There is [a bug](https://github.com/GradleUp/shadow/issues/882) in Shadow Gradle plugin which may lead to some build issues when both Decoroutinator as a Gradle plugin and Shadow are applied. But there are some [workarounds](https://github.com/GradleUp/shadow/issues/882#issuecomment-1715703146) for it. See more at https://github.com/reformator14/stacktrace-decoroutinator/issues/46. 

### Problem with Jacoco
Using Jacoco and Decoroutinator as a Java agent may lead to the loss of code coverage. It's [a common Jacoco Problem](https://www.eclemma.org/jacoco/trunk/doc/classids.html). In order not to lose coverage, make sure that the Jacoco agent comes before the Decoroutinator agent. See more at https://github.com/reformator14/stacktrace-decoroutinator/issues/24.

### Usage with Robolectric
[Robolectric](https://robolectric.org/) puts some Decoroutinator classes in different class loaders by default, which leads to an exception during the execution of tests. To fix this please add the following config to your `build.gradle.kts`:
```kotlin
android {
    testOptions {
        unitTests.all {
            it.systemProperty(
                "org.robolectric.packagesToNotAcquire",
                "dev.reformator.stacktracedecoroutinator."
            )
        }
    }
}
```

### Troubleshooting
You can call function `DecoroutinatorCommonApi.getStatus { it() }` at runtime to check if Decoroutinator has been successfully installed.

### Communication
Feel free to ask any question at [Discussions](https://github.com/reformator14/stacktrace-decoroutinator/discussions).
