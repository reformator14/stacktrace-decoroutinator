import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar

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
    val jvmAgentShadowJarTask = project(":stacktrace-decoroutinator-jvm-agent").tasks.shadowJar
    dependsOn(jvmAgentShadowJarTask)
    jvmArgs("-javaagent:${jvmAgentShadowJarTask.get().archiveFile.get().asFile.absolutePath}")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
