import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(project(":tests:methods-with-spaces-tests"))

    testRuntimeOnly(libs.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
    // Test against the published artifact (gr8-minimized, see jvm-agent/build.gradle.kts), not
    // shadowJar's own output - shrinking can break things relocation alone did not.
    val jvmAgentGr8JarTask = project(":stacktrace-decoroutinator-jvm-agent").tasks.named("gr8MinimizedShadowedJar")
    dependsOn(jvmAgentGr8JarTask)
    jvmArgs("-javaagent:${jvmAgentGr8JarTask.get().outputs.files.single { it.extension == "jar" }.absolutePath}")
    systemProperty(
        "dev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy",
        "SYSTEM_RESOURCE"
    )
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_9
    }
}

sourceSets {
    test {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}
