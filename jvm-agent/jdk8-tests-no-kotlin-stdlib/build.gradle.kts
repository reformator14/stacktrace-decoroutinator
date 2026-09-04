plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(project(":tests:no-kotlin-stdlib"))

    testRuntimeOnly(libs.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
    // Test against the published artifact (gr8-minimized, see jvm-agent/build.gradle.kts), not
    // shadowJar's own output - shrinking can break things relocation alone did not.
    val jvmAgentGr8JarTask = project(":stacktrace-decoroutinator-jvm-agent").tasks.named("gr8MinimizedShadowedJar")
    dependsOn(jvmAgentGr8JarTask)
    jvmArgs("-javaagent:${jvmAgentGr8JarTask.get().outputs.files.single { it.extension == "jar" }.absolutePath}")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
