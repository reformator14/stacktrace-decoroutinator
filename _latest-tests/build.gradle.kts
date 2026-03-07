plugins {
    alias(libs.plugins.android.application) version libs.versions.agp.latest apply false
    alias(libs.plugins.kotlin.jvm) version libs.versions.kotlin.latest apply false
}

buildscript {
    dependencies {
        classpath("_plugins:build-dependencies")
    }
}
