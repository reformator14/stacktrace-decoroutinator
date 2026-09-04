import dev.reformator.decoroutinatortransformbasecontinuation.decoroutinatorTransformedBaseContinuationAttribute

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.decoroutinator.transform.base.continuation)
}

repositories {
    mavenCentral()
}

dependencies {
    testRuntimeOnly(libs.decoroutinator.generator.jvm)
    testRuntimeOnly(libs.decoroutinator.mh.invoker)
    testRuntimeOnly(libs.decoroutinator.naive.base.continuation.accessor)
    testRuntimeOnly(libs.decoroutinator.common)

    testImplementation(kotlin("test"))
    testImplementation(libs.decoroutinator.methods.with.spaces.tests)
}

afterEvaluate {
    configurations.testRuntimeClasspath.get().attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}
