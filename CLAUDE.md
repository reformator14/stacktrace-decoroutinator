# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Stacktrace-decoroutinator is a Kotlin library that recovers complete stack traces in exceptions thrown in Kotlin coroutines. It works by intercepting coroutine resumption (`BaseContinuation.resumeWith`) and generating auxiliary "spec methods" at runtime whose names match the coroutine call stack, producing real stack frames when exceptions are thrown.

Targets JVM 1.8+ and Android API 14+ (MethodHandle API requires Android 8+).

## Build Commands

```bash
# Run all JVM tests
./gradlew test

# Full build (includes tests)
./gradlew build

# Run specific module tests
./gradlew :stacktrace-decoroutinator-jvm:test
./gradlew :stacktrace-decoroutinator-jvm-agent:jvm-agent-tests:test

# _tests build (Gradle plugin + compatibility tests, separate project)
./gradlew initTestsGradleWrapper  # required once before running _tests
cd _tests && ../gradlew test

# Android tests (requires emulator, run from _tests)
cd _tests && ../gradlew connectedAndroidTest

# _latest-tests build (latest Kotlin/Gradle/AGP version tests, separate project)
cd _latest-tests && ./gradlew test

# Android latest tests (requires emulator, run from _latest-tests)
cd _latest-tests && ./gradlew connectedAndroidTest
```

Build requires JDK 21 (and JDK 8 for JDK8-specific test modules). Gradle wrapper version is 9.2.1. `GRADLE_OPTS: -Xmx2g` is used in CI.

## Project Structure

Multi-module Gradle project (Kotlin DSL) with ~40 submodules. Key layout:

- **`_plugins/`** — Custom Gradle plugins (bytecode-processor, force-variant-java-version) included as composite builds
- **`_tests/`** — Gradle plugin + compatibility test projects (composite build, run separately)
- **`_latest-tests/`** — Tests against the latest Kotlin, Gradle, and AGP versions (separate composite build with its own gradlew). Re-compiles test sources from the main project with the latest Kotlin compiler. Submodules: `jvm/dynamic-agent-tests`, `jvm/gradle-plugin-tests`, `android/gradle-plugin-tests`, `tests`, `tests/methods-with-spaces-tests`

### Core modules (published to Maven Central):

| Module | Purpose |
|--------|---------|
| `provider` | Service provider interface (Java SPI) |
| `common` | Platform-agnostic awakening logic, bytecode processor plugin integration |
| `generator-jvm` | JVM code generation, transformation metadata, class loading |
| `generator-android` | Android-specific code generation |
| `class-transformer` | ASM-based bytecode transformations |
| `spec-method-builder` | Generates auxiliary "spec methods" via ASM |
| `mh-invoker`, `mh-invoker-jvm`, `mh-invoker-android` | MethodHandle-based spec method invocation |
| `runtime-settings` | Runtime configuration/feature flags |
| `jvm` | JVM runtime API (`DecoroutinatorJvmApi.install()`); uses ByteBuddy to obtain an `Instrumentation` instance |
| `jvm-agent` | Java agent JAR |
| `jvm-agent-common` | Shared agent transformation logic |
| `gradle-plugin` | Gradle plugin for build-time bytecode transformation |
| `intrinsics` | Compiler intrinsics for compile-time optimizations |

### `_latest-tests` infrastructure:

The `_latest-tests/_plugins/root-dependencies-loader` composite build plugin provides a DSL for loading prebuilt JARs from the main build into `_latest-tests` subprojects. It defines `DependenciesConfiguration` (split into `api` and `runtime` parts) and extension properties on `Project` in `root-dependencies-loader-dsl.kt`:

- **`bytecodeProcessorIntrinsics/Api/Plugins/GradlePlugin`** — bytecode-processor plugin JARs from `_plugins/bytecode-processor/`
- **`decoroutinatorProvider/RuntimeSettings/Common`** — core library JARs
- **`decoroutinatorTests/TestsDuplicateEntityJar/TestsMethodWithSpacesTests`** — test utility JARs
- **`decoroutinatorSpecMethodBuilder/ClassTransformer/MhInvoker/GeneratorJvm/JvmAgentCommon/Jvm`** — JVM runtime JARs
- **`decoroutinatorGradlePlugin`** — Gradle plugin JAR

Each property mirrors the Gradle dependency scope of the original module (`api` deps → `addApi`, `implementation`/`runtimeOnly` deps → `addRuntime`). When adding a new property, read the corresponding `build.gradle.kts` to map its deps. The `DependencyHandler` extensions `implementation(DependenciesConfiguration)`, `api(...)`, `compileOnly(...)`, `runtimeOnly(...)`, `testImplementation(...)`, `testRuntimeOnly(...)` apply configurations to the correct Gradle configurations.

### Test modules:
- `tests` — Shared test utilities (retrace tool, runtime tests); submodules: `tests:custom-loader`, `tests:methods-with-spaces-tests`, `tests:naive-base-continuation-accessor`, `tests:duplicate-entity-jar`, `tests:aar`, `tests:bytecode-processor`
- `jvm:jdk8-tests`, `jvm-agent:tests`, `jvm-agent:jdk8-tests` — JVM integration tests
- `_tests/jvm/*`, `_tests/jdk8/*`, `_tests/android/*`, `_tests/android-legacy/*` — Gradle plugin compatibility tests (separate composite build)
- `_latest-tests/jvm/*`, `_latest-tests/android/*`, `_latest-tests/tests/*` — Latest Kotlin/Gradle/AGP version tests (separate composite build)

## Architecture

### Three installation methods (JVM):
1. **Gradle plugin** (build-time) — transforms bytecode during compilation
2. **Java agent** (runtime) — registers a `ClassFileTransformer` via `java.lang.instrument`
3. **Programmatic API** (runtime) — `DecoroutinatorJvmApi.install()`; uses ByteBuddy to obtain an `Instrumentation` instance, then registers the same transformer as the agent

### Core flow:
```
API Layer → Provider (SPI) → Awakener → SpecMethodBuilder (ASM) → MethodHandle Invoker
```

The awakening mechanism in `common/src/main/kotlin/internal/awakener.kt` captures the coroutine call stack and calls generated spec methods in order, so exception stack traces contain the full coroutine call chain.

### Bytecode processing:
Custom `_plugins/bytecode-processor` Gradle plugin applies compile-time transformations: class renaming, invocation skipping/redirecting, constant loading, static conversion. Processors are configured per-module in individual `build.gradle.kts` files.

### Key conventions:
- Source files are often named by their content (e.g., `api-jvm.kt`, `awakener.kt`, `provider-impl.kt`) rather than by class name
- Version is set once in root `build.gradle.kts` (the `allprojects { version = "..." }` block)
- All published modules use the group `dev.reformator.stacktracedecoroutinator`
- Dependencies are managed via `gradle/libs.versions.toml` version catalog
- Kotlin incremental compilation is disabled (`kotlin.incremental=false` in `gradle.properties`)

### Internal implementation gotchas:

**`$label` high-bit flag**: Kotlin's coroutine state machine sets the high bit (`or Int.MIN_VALUE`) on `$label` when actively executing inside `invokeSuspend` (re-entry guard). Any code using `$label` as an array index must strip the high bit: `label and Int.MAX_VALUE`. The sentinel `NONE_LABEL = Int.MIN_VALUE / 2` means "no debug metadata"; `UNKNOWN_LABEL = NONE_LABEL - 1` means "label field inaccessible". See `tailCallDeoptimize` in `provider-impl.kt` for the canonical guard pattern.

**Raw result values**: The awakener passes raw `Any?` values (not `Result<T>` wrappers) through the coroutine chain. Since `Result<T>` is `@JvmInline`, a `Result.Failure` object IS the raw failure value and `Result.success(x)` at JVM bytecode level is just `x`. Therefore `Result.success(createFailure(e))` correctly delivers a failure to `resumeWith`. The `toResult` extension property is `@SkipInvocations` — a no-op cast at bytecode level.

**`releaseIntercepted()` contract**: Must be called after `invokeSuspend` completes for both success and exception paths, but NOT when it returns `COROUTINE_SUSPENDED`. This mirrors `BaseContinuationImpl.resumeWith` in the standard library.

**Bytecode intrinsics**: `@SkipInvocations` removes the call instruction leaving the receiver on the stack; `@ChangeClassName` renames the class in bytecode; `@ChangeInvocationsOwner` redirects method call owner; `@GetOwnerClass` injects the class literal; `@MethodNameConstant` injects the method name as a string constant.

**`supportsVarHandle` check in `mh-invoker`**: `_supportVarHandle.verifyVarHandle()` in `mh-invoker/src/main/kotlin/internal/mh-invoker.kt` must use `_supportVarHandle::class.java` (not `_support::class.java`) in the `findVarHandle` call. Using the wrong class causes `NoSuchFieldException`, silently disabling VarHandle-based label reads and forcing a slower reflection fallback on every coroutine resume.

**Spec method chain execution order**: Spec methods create a nested real JVM call chain (outermost spec method calls inner, which calls inner, etc.). `DecoroutinatorSpec.resumeNext` calls `nextContinuation.callInvokeSuspend` — the `nextContinuation` is always one level *below* the current spec (the spec represents the line number of the frame *above* the continuation it resumes). Execution order: innermost continuation first (via `resumeNext` of the innermost spec), then each outer continuation in turn.

**`SpecCache.specMethod` benign race**: Written without synchronization (two threads may both compute and write the same handle), which is benign since MethodHandle instances are immutable and the computation is pure.

**`ManualContinuation.$decoroutinator$cacheField` can be JVM-null despite its non-null Kotlin type**: The bytecode transformer (`tryAddManualContinuation`) only runs the `PUTSTATIC` for the (`static`) `$decoroutinator$cacheField` in `<clinit>` when `fillUnknownElementsWithClassName` is `true` at class-load time; when false, the raw JVM field stays at its default `null`, even though the interface declares it `val $decoroutinator$cacheField: SpecCache` (non-null). This is only sound because `ManualContinuation.$decoroutinator$cache`'s getter checks `provider.fillUnknownElementsWithClassName` itself and returns `provider.nullElementSpecCache` *before* ever touching `$decoroutinator$cacheField` — since that flag is a process-lifetime-constant `val`, the `<clinit>`-time gate and the getter's runtime gate always agree. Never read `$decoroutinator$cacheField` directly without first replicating that same flag check.

**`LazilyCachedContinuation` vs `ManualContinuation`**: Both bytecode-inject a `ContinuationCached` implementation into external (non-project) classes via `class-transformer/.../classTransformer.kt`, but for different situations. `ManualContinuation` (`tryAddManualContinuation`, whitelist `manualContinuationsInternalClassNames`, e.g. `kotlinx.coroutines.internal.ScopeCoroutine`) is for classes whose real `getStackTraceElement()` is class-wide constant (e.g. always `null`) — it eagerly bakes one synthetic `SpecCache` into a *static* field at `<clinit>`. `LazilyCachedContinuation` (`tryAddLazilyCachedContinuation`, whitelist `lazilyCachedContinuationsInternalClassNames`, e.g. `kotlinx.coroutines.debug.internal.DebugProbesImpl$CoroutineOwner`) is for classes whose real `getStackTraceElement()` carries genuine, varying per-*instance* data — it lazily computes and caches a `SpecCache` into an *instance* field the first time `$decoroutinator$cache` is read, which is always correct because a real per-instance value never changes after construction. Both mechanisms also patch the class's own `getStackTraceElement()` (via the shared `updateGetStackTraceElementMethod`) to serve the cache once populated, so external callers of that method benefit too — but the two patches call *different* things to fetch the cache, and this difference is load-bearing: `ManualContinuation`'s patch calls the smart `$decoroutinator$cache` getter (safe — its computation never calls back into `getStackTraceElement()`), while `LazilyCachedContinuation`'s patch calls the *raw* `$decoroutinator$cacheField` getter, not the smart `$decoroutinator$cache` getter. Calling the smart getter there would recurse infinitely: `$decoroutinator$cache`'s own computation calls `getStackTraceElement()` to get the real value, which — if patched to call the smart getter — would re-enter `$decoroutinator$cache` before the field is ever populated.

**`SpecCache.element` is nullable; `provider.nullElementSpecCache`** is the shared singleton (`element = null`, `specMethod` pre-set to `methodHandleInvoker.unknownSpecMethodHandle`) used whenever a `ManualContinuation`/`LazilyCachedContinuation` frame has no usable element and `fillUnknownElementsWithClassName` is off. Reusing one singleton (rather than returning plain `null`) means the caller — the injected `$decoroutinator$cache` field-based caching — never has to distinguish "not yet computed" from "computed, nothing useful," so it never recomputes.

**`Method.invoke` wraps checked exceptions in `InvocationTargetException`**: When calling a protected/private method via reflection (e.g., `ClassLoader.findClass`), any checked exception thrown by the method is wrapped in `InvocationTargetException`. Direct `catch (e: ClassNotFoundException)` at the call site of `Method.invoke(...)` will NOT catch it — you must catch `InvocationTargetException` and rethrow `e.cause`. See `generator-android/specMethodsFactory-generator-android.kt`'s `ClassLoader.findClass` extension for the canonical pattern.

**`ArtifactWalker.onFile` stream ownership**: The `reader: () -> InputStream` lambda creates a new stream per call. **The caller owns the stream and must close it** — neither `ZipArtifactBuilder.addFile` nor `DirectoryArtifact.addFile` closes the `body` parameter. Always wrap in `reader().use { ... }` or `(expr).use { ... }`.

**`classBodyResolver` must not trigger class loading**: `transformClassBody`'s `classBodyResolver: (className: String) -> InputStream?` parameter is called during class transformation (inside a `ClassFileTransformer` callback). Implementations must use `ClassLoader.getResourceAsStream` (reads raw bytes) rather than `Class.forName`/`ClassLoader.loadClass`, because class loading from within a class-loading callback causes recursive loading and `ClassCircularityError`. The `className` argument is a dotted binary name (e.g., `com.example.Foo$Bar`); in the agent path, convert to a path with `className.internalName + ".class"`. This is why the enum `DecoroutinatorMetadataInfoResolveStrategy` (and its `CLASS`/`SYSTEM_RESOURCE_AND_CLASS` strategies) was removed in #81.

**`tailCallDeoptimize` NONE_LABEL vs UNKNOWN_LABEL distinction**: Both sentinels have bit 31 set, but `tailCallDeoptimize` in `provider-impl.kt` treats them differently. The guard `label != NONE_LABEL && label and Int.MIN_VALUE != 0` means: NONE_LABEL (class has no metadata, `_elementsByLabel == null`) → condition is false → wrap the completion anyway (safe, since no re-entry risk). UNKNOWN_LABEL (field inaccessible) → condition is true → skip wrapping, treating it as "possibly executing" (conservative, avoids wrapping a running coroutine). This asymmetry is intentional: unknown metadata is conservative in the "wrap" direction; inaccessible field is conservative in the "don't wrap" direction.

**`SpecMethodsFactoryImpl.init` listener-then-snapshot**: The `init` block adds the listener first, then iterates `transformedClasses` under `classSpecsByNameUpdateLock`. This ensures no class is missed: any class registered before the listener was added is caught by the snapshot iteration, and the listener callback itself acquires the same lock so it cannot race with the snapshot. A class registered concurrently may be processed twice (listener call + snapshot entry), but `register` is idempotent (harmless overwrite).

**`Artifact.transform` two-pass walk in `gradle-class-transformer.kt`**: First pass processes all non-module-info files (transforms class bytecode, accumulates `readProviderModule`/`containsBaseContinuation` flags). Second pass processes only module-info files (patches `requires`/`provides` based on first-pass results). `doesNeedTransformation` may stop the first pass early via `onFile` returning `false`, which also skips the second pass — but this is correct because `doesNeedTransformation` only needs a boolean. `transformTo`'s `onFile` always returns `true`, so both passes always complete when doing actual transformation.

**Groovy DSL initialization via `@file:ChangeClassName`**: `gradle-plugin/src/main/kotlin/groovy-dsl-initializer.kt` is an intrinsic placeholder with `@file:ChangeClassName(toName = "...GroovyDslInitializer", deleteAfterChanging = true)` that defines `fun initGroovyDsl(target: Project): Unit = fail()`. At Kotlin compile time, the call `initGroovyDsl(target)` in `common-gradle-plugin.kt` resolves to this Kotlin function. After bytecode processing, all references to the Kotlin file class are replaced with `GroovyDslInitializer` (the Groovy class in `src/main/groovy/GroovyDslInitializer.groovy`), and the Kotlin placeholder class file is deleted. The Groovy class has the real `static void initGroovyDsl(Project)` that registers Groovy DSL extension methods. This avoids `ServiceLoader.load(...)` which uses the thread's context classloader — on Gradle 9 build-operations worker threads, the context classloader doesn't include the plugin JAR, causing `NoSuchElementException`.