import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar

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
    val jvmAgentShadowJarTask = project(":stacktrace-decoroutinator-jvm-agent").tasks.shadowJar
    dependsOn(jvmAgentShadowJarTask)
    jvmArgs("-javaagent:${jvmAgentShadowJarTask.get().archiveFile.get().asFile.absolutePath}")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:-module"))
}
