plugins {
    alias(libs.plugins.root.dependencies.loader)
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(decoroutinatorJvm)
    testImplementation(project(":tests:methods-with-spaces-tests"))
    testImplementation(libs.junit5.platform.launcher)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    test {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}
