import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
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
    val jvmAgentShadowJarTask = project(":stacktrace-decoroutinator-jvm-agent").tasks.shadowJar
    dependsOn(jvmAgentShadowJarTask)
    jvmArgs("-javaagent:${jvmAgentShadowJarTask.get().archiveFile.get().asFile.absolutePath}")
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
