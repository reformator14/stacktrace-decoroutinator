plugins {
    alias(libs.plugins.kotlin.jvm)
}
repositories {
    mavenCentral()
}

dependencies {
    testCompileOnly(project(":intrinsics"))

    testImplementation(project(":stacktrace-decoroutinator-jvm"))
    testImplementation(kotlin("test"))
    testImplementation(project(":tests:methods-with-spaces-tests"))
    testImplementation(libs.junit5.platform.launcher)
    testImplementation(libs.coroutines.core.build)
}

val testReloadBaseConfigurationTask = tasks.register<Test>("testReloadBaseConfiguration") {
    useJUnitPlatform()
    classpath = tasks.test.get().classpath
}
tasks.test {
    useJUnitPlatform()
    systemProperty("installDecoroutinator", true)
    dependsOn(testReloadBaseConfigurationTask)
}

kotlin {
    jvmToolchain(8)
}
