plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(project(":tests:no-kotlin-stdlib"))
    // implementation-scoped in tests:no-kotlin-stdlib, so it doesn't propagate here - needed on
    // this module's own compile module path for its module-info.java's requires clause.
    testImplementation(libs.jupiter.api)

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
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:-module"))
}
