plugins {
    alias(libs.plugins.root.dependencies.loader)
    `java-library`
}

group = "_plugins"

rootDependenciesLoader {
    rootPath = file("../../..")
}

repositories {
    mavenCentral()
}

dependencies {
    api(bytecodeProcessorGradlePlugin)
}
