import dev.reformator.decoroutinatortransformbasecontinuation.decoroutinatorTransformedBaseContinuationAttribute

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.decoroutinator.transform.base.continuation)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.decoroutinator.methods.with.spaces.tests)

    testRuntimeOnly(libs.decoroutinator.generator.jvm)
    testRuntimeOnly(libs.decoroutinator.mh.invoker.jvm)
    testRuntimeOnly(libs.decoroutinator.naive.base.continuation.accessor)
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
