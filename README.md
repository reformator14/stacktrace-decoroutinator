[![Maven Central](https://img.shields.io/maven-central/v/dev.reformator.stacktracedecoroutinator/stacktrace-decoroutinator-common 'JVM artifact')](https://central.sonatype.com/artifact/dev.reformator.stacktracedecoroutinator/stacktrace-decoroutinator-jvm)
[![Gradle Plugin](https://img.shields.io/gradle-plugin-portal/v/dev.reformator.stacktracedecoroutinator 'Gradle plugin')](https://plugins.gradle.org/plugin/dev.reformator.stacktracedecoroutinator)
[![Kotlin Foundation Grantee](https://img.shields.io/badge/Kotlin%20Foundation-Grantee-blue 'Winner of the Kotlin Foundation Grants Program')](https://kotlinfoundation.org/news/grants-program-winners-25/)

# Stacktrace-decoroutinator
Library for recovering stack traces in exceptions thrown in Kotlin coroutines.

Supports JVM 1.8 or higher and Android API 14 or higher.

## Motivation
Coroutines are a significant Kotlin feature that allows you to write asynchronous code in a synchronous style.

This works perfectly until you need to debug problems in your code.

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
The stack trace is missing calls to `fun3` and `fun2`, so it does not represent the true coroutine call stack.

In complex systems, even more calls may be missing. This can make debugging much more difficult.

Some examples of suffering from this problem:
- https://github.com/arrow-kt/arrow/issues/2647
- https://stackoverflow.com/questions/54349418/how-to-recover-the-coroutines-true-call-trace
- https://stackoverflow.com/questions/69226016/how-to-get-full-exception-stacktrace-when-using-await-on-completablefuture

The Kotlin team is aware of the problem and has come up with a [solution](https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/debugging.md#stacktrace-recovery), but it only addresses some cases.
For example, the exception from the example above still lacks some calls.

## Solution
Decoroutinator replaces the coroutine resumption implementation.

It generates methods at runtime with names that match the entire coroutine call stack.

These methods do nothing except call each other sequentially in coroutine call stack order, creating real JVM stack frames.

Thus, if the coroutine throws an exception, the stack trace reflects the full coroutine call chain.

Check out [the Decoroutinator playground](https://decoroutinator.reformator.dev/playground/).

## JVM
There are three ways to enable Decoroutinator on the JVM:
1. If you build your project with Gradle, apply the Gradle plugin with id `dev.reformator.stacktracedecoroutinator`.
2. Add `-javaagent:/path/to/stacktrace-decoroutinator-jvm-agent-2.6.3.jar` to your JVM startup arguments. The corresponding dependency is `dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-jvm-agent:2.6.3`.
3. Add the dependency `dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-jvm:2.6.3` and call `DecoroutinatorJvmApi.install()`.

The first option generates auxiliary methods at build time; the other two use the Java instrumentation API at runtime.

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

## Android
For Android, the only supported option is to apply the Gradle plugin to your application project:
```kotlin
plugins {
    id("dev.reformator.stacktracedecoroutinator") version "2.6.3"
}
```
Note that Decoroutinator uses the [MethodHandle API](https://developer.android.com/reference/java/lang/invoke/MethodHandle), which requires Android API 26 (Android 8) or higher. Stack trace recovery does not work on older Android versions.

## Embedding DebugProbes
Decoroutinator also allows embedding [DebugProbes](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-debug/kotlinx.coroutines.debug/-debug-probes/) into your Android application.
DebugProbes is a mechanism for dumping coroutine state and stack traces at runtime, useful for debugging.
```kotlin
stacktraceDecoroutinator {
    embedDebugProbesForAndroid = true
    // or the following if you want to embed DebugProbes only for tests:
    // embedDebugProbesForAndroidTest = true
}
```

## Using the Gradle Plugin Only for Tests
If you want to use Decoroutinator for tests only, the recommended approach is to place your tests in a separate Gradle subproject and apply the Decoroutinator Gradle plugin there.
If you cannot separate your tests, you can still restrict it by adding the following to your `build.gradle.kts`:
```kotlin
stacktraceDecoroutinator {
    androidTestsOnly = true
}
```

## Using the Gradle Plugin on Android with Minification Enabled
Add the following ProGuard configuration to your `build.gradle.kts`:
```kotlin
android {
    buildTypes {
        release {
            proguardFiles(decoroutinatorAndroidProGuardRules())
        }
    }
}
```

## Using Decoroutinator with Kotest
[Kotest](https://kotest.io) 6.0 offers Decoroutinator support out of the box.
See the documentation on how to integrate [here](https://kotest.io/docs/extensions/decoroutinator.html).

## Known Issue: Shadow Gradle Plugin
There is [a bug](https://github.com/GradleUp/shadow/issues/882) in the Shadow Gradle plugin that may cause build issues when both the Decoroutinator Gradle plugin and Shadow are applied. Several [workarounds](https://github.com/GradleUp/shadow/issues/882#issuecomment-1715703146) are available. See https://github.com/reformator14/stacktrace-decoroutinator/issues/46 for details.

## Known Issue: Jacoco
Using Decoroutinator as a Java agent alongside Jacoco may result in lost code coverage. This is [a known Jacoco limitation](https://www.eclemma.org/jacoco/trunk/doc/classids.html). To avoid losing coverage, ensure the Jacoco agent is listed before the Decoroutinator agent in your JVM arguments. See https://github.com/reformator14/stacktrace-decoroutinator/issues/24 for details.

## Usage with Robolectric
[Robolectric](https://robolectric.org/) places some Decoroutinator classes in separate class loaders by default, which causes an exception during test execution. To fix this, add the following to your `build.gradle.kts`:
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

## Troubleshooting
To check whether Decoroutinator has been successfully installed at runtime, call:
```kotlin
DecoroutinatorCommonApi.getStatus { it() }
```

## Communication
Feel free to ask any questions at [Discussions](https://github.com/reformator14/stacktrace-decoroutinator/discussions).
