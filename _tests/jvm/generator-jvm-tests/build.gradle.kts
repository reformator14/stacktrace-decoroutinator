import dev.reformator.decoroutinatortransformbasecontinuation.decoroutinatorTransformedBaseContinuationAttribute
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    compilerOptions {
        jvmTarget = JvmTarget.JVM_9
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

sourceSets {
    test {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}
