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
./gradlew :jvm-agent:tests-ja:test

# Gradle plugin tests (separate project, requires main build first)
./gradlew gradlePluginTest

# Android tests (requires emulator)
./gradlew gradlePluginAndroidTest
./gradlew gradlePluginLegacyAndroidTest

# Compatibility tests
./gradlew :latest-kotlin-gradle-plugin-test:test
./gradlew latestGradleTest
```

Build requires JDK 21 (and JDK 8 for JDK8-specific test modules). Gradle wrapper version is 9.2.1. `GRADLE_OPTS: -Xmx2g` is used in CI.

## Project Structure

Multi-module Gradle project (Kotlin DSL) with ~40 submodules. Key layout:

- **`_plugins/`** — Custom Gradle plugins (bytecode-processor, gradle-plugin-test, force-variant-java-version) included as composite builds
- **`_gradle_plugin_tests/`** — Gradle plugin tests (standalone project, invoked via GradleConnector)
- **`_tests/`** — Latest Kotlin compatibility test projects (composite builds)
- **`_latest-gradle-test/`** — Latest Gradle version compatibility tests

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
| `jvm` | JVM runtime API (`DecoroutinatorJvmApi.install()`) |
| `jvm-agent` | Java agent JAR (uses ByteBuddy) |
| `jvm-agent-common` | Shared agent transformation logic |
| `gradle-plugin` | Gradle plugin for build-time bytecode transformation |
| `intrinsics` | Compiler intrinsics for compile-time optimizations |

### Test modules:
- `test-utils`, `test-utils-jvm` — Shared test utilities (retrace tool, custom classloader, stubs)
- Modules named `*-tests-*` or `*-jdk8-tests-*` contain integration/compatibility tests

## Architecture

### Three installation methods (JVM):
1. **Gradle plugin** (build-time) — transforms bytecode during compilation
2. **Java agent** (runtime) — uses ByteBuddy to retransform loaded classes
3. **Programmatic API** (runtime) — `DecoroutinatorJvmApi.install()`

### Core flow:
```
API Layer → Provider (SPI) → Awakener → SpecMethodBuilder (ASM) → MethodHandle Invoker
```

The awakening mechanism in `common/src/main/kotlin/internal/awakener.kt` captures the coroutine call stack and calls generated spec methods in order, so exception stack traces contain the full coroutine call chain.

### Bytecode processing:
Custom `_plugins/bytecode-processor` Gradle plugin applies compile-time transformations: class renaming, invocation skipping/redirecting, constant loading, static conversion. Processors are configured per-module in individual `build.gradle.kts` files.

### Key conventions:
- Source files are often named by their content (e.g., `api-jvm.kt`, `awakener.kt`, `provider-impl.kt`) rather than by class name
- Version is set once in root `build.gradle.kts` (`version = "2.6.2-SNAPSHOT"`)
- All published modules use the group `dev.reformator.stacktracedecoroutinator`
- Dependencies are managed via `gradle/libs.versions.toml` version catalog
- Kotlin incremental compilation is disabled (`kotlin.incremental=false` in `gradle.properties`)