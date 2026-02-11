plugins {
    alias(libs.plugins.kotlin.jvm) version libs.versions.kotlin.build.get() apply false
    alias(libs.plugins.kotlin.android) version libs.versions.kotlin.build.get() apply false
    alias(libs.plugins.android.library) version libs.versions.agp.build.get() apply false
    alias(libs.plugins.shadow) version libs.versions.plugin.shadow.get() apply false
    alias(libs.plugins.dokka) version libs.versions.plugin.dokka.get() apply false
    alias(libs.plugins.gradle.publish) version libs.versions.plugin.gradle.publish.get() apply false
}

allprojects {
    group = "dev.reformator.stacktracedecoroutinator"
    version = "2.6.2-SNAPSHOT"
}

tasks.register("initTestsGradleWrapper") {
    val sourceFile = file("gradle/wrapper/gradle-wrapper.properties")
    val targetFile = file("_tests/gradle/wrapper/gradle-wrapper.properties")
    inputs.file(sourceFile)
    outputs.file(targetFile)
    doLast {
        targetFile.parentFile.mkdirs()
        sourceFile.copyTo(targetFile, overwrite = true)
    }
}
