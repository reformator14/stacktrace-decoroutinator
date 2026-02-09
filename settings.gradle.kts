rootProject.name = "stacktrace-decoroutinator"
include(
    "provider",
    "common",
    "generator-jvm",
    "gradle-plugin",
    "jvm-agent-common",
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
    "test-utils",
    "test-utils:base-continuation-accessor-stub",
    "test-utils:base-continuation-accessor-reflect-stub",
    "test-utils-jvm",
    "test-utils:custom-loader",
    "generator-jvm:jdk8-tests-g",
    "gradle-plugin:tests-gp",
    "gradle-plugin:android-tests",
    "gradle-plugin:jdk8-tests-gp",
    "gradle-plugin:duplicate-entity-jar-builder",
    "gradle-plugin:android-legacy-tests",
    "gradle-plugin:empty-module-tests",
    "gradle-plugin:embedded-debug-probes-stdlib",
    "gradle-plugin:embedded-debug-probes-xcoroutines",
    "gradle-plugin:base-continuation-accessor",
    "jvm-agent:tests-ja",
    "jvm-agent:jdk8-tests-ja",
    "jvm:jdk8-tests-j",
    "mh-invoker-jvm:jdk8-tests-mij",
    "jvm-agent-common:suspend-class-stub",
    "generator-jvm:android-wrong-generator-tests",
    "gradle-plugin:gradle-groovy-dsl-tests",
    "gradle-plugin:aar-builder"
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

includeBuild("_plugins/bytecode-processor")
includeBuild("_plugins/gradle-plugin-test")
includeBuild("_plugins/force-variant-java-version")

includeBuild("_tests/latest-kotlin-gradle-plugin-test")
includeBuild("_tests/latest-kotlin-kmp-gradle-plugin-test")

pluginManagement {
    includeBuild("_plugins/bytecode-processor")
    includeBuild("_plugins/gradle-plugin-test")
    includeBuild("_plugins/force-variant-java-version")
}

plugins {
    id("com.gradleup.nmcp.settings") version "1.4.2"
}

nmcpSettings {
    centralPortal {
        username = providers.gradleProperty("sonatype.username").orNull
        password = providers.gradleProperty("sonatype.password").orNull
        publishingType = "USER_MANAGED"
    }
}
