rootProject.name = "stacktrace-decoroutinator"

includeBuild("_plugins/bytecode-processor")
includeBuild("_plugins/force-variant-java-version")
includeBuild("_plugins/delete-signature-checksums")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    includeBuild("_plugins/bytecode-processor")
    includeBuild("_plugins/force-variant-java-version")
    includeBuild("_plugins/delete-signature-checksums")
}

plugins {
    id("com.gradleup.nmcp.settings") version "1.4.2"
}

include(
    "provider",
    "common",
    "generator-jvm",
    "gradle-plugin",
    "gradle-plugin:embedded-debug-probes-stdlib",
    "gradle-plugin:embedded-debug-probes-xcoroutines",
    "gradle-plugin:base-continuation-accessor",
    "jvm-agent-common",
    "jvm-agent-common:suspend-class-stub",
    "jvm",
    "jvm-agent",
    "generator-android",
    "mh-invoker",
    "mh-invoker-android",
    "mh-invoker-jvm",
    "runtime-settings",
    "class-transformer",
    "spec-method-builder",

    "intrinsics",
    "jvm:jdk8-tests",
    "jvm-agent:tests",
    "jvm-agent:jdk8-tests",
    "jvm-agent:tests-no-kotlin-stdlib",
    "jvm-agent:jdk8-tests-no-kotlin-stdlib",
    "tests",
    "tests:custom-loader",
    "tests:no-kotlin-stdlib",
    "tests:methods-with-spaces-tests",
    "tests:naive-base-continuation-accessor",
    "tests:duplicate-entity-jar",
    "tests:aar",
    "tests:bytecode-processor"
)
project(":provider").name = "stacktrace-decoroutinator-provider"
project(":common").name = "stacktrace-decoroutinator-common"
project(":generator-jvm").name = "stacktrace-decoroutinator-generator-jvm"
project(":gradle-plugin").name = "stacktrace-decoroutinator-gradle-plugin"
project(":jvm-agent-common").name = "stacktrace-decoroutinator-jvm-agent-common"
project(":jvm").name = "stacktrace-decoroutinator-jvm"
project(":jvm-agent").name = "stacktrace-decoroutinator-jvm-agent"
project(":generator-android").name = "stacktrace-decoroutinator-generator-android"
project(":mh-invoker").name = "stacktrace-decoroutinator-mh-invoker"
project(":mh-invoker-android").name = "stacktrace-decoroutinator-mh-invoker-android"
project(":mh-invoker-jvm").name = "stacktrace-decoroutinator-mh-invoker-jvm"
project(":runtime-settings").name = "stacktrace-decoroutinator-runtime-settings"
project(":class-transformer").name = "stacktrace-decoroutinator-class-transformer"
project(":spec-method-builder").name = "stacktrace-decoroutinator-spec-method-builder"

project(":jvm:jdk8-tests").name = "jvm-jdk8-tests"
project(":jvm-agent:tests").name = "jvm-agent-tests"
project(":jvm-agent:jdk8-tests").name = "jvm-agent-jdk8-tests"
project(":jvm-agent:tests-no-kotlin-stdlib").name = "jvm-agent-tests-no-kotlin-stdlib"
project(":jvm-agent:jdk8-tests-no-kotlin-stdlib").name = "jvm-agent-jdk8-tests-no-kotlin-stdlib"

nmcpSettings {
    centralPortal {
        username = providers.gradleProperty("sonatype.username").orNull
        password = providers.gradleProperty("sonatype.password").orNull
        publishingType = "USER_MANAGED"
    }
}
