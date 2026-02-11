import dev.reformator.decoroutinatortransformbasecontinuation.decoroutinatorTransformedBaseContinuationAttribute
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.decoroutinator.transform.base.continuation)
}

buildscript {
    dependencies {
        classpath(libs.android.gradle.plugin.build)
    }
}

repositories {
    mavenCentral()
}

val androidJar = rootProject.file("local.properties").inputStream().use { input ->
    val properties = Properties()
    properties.load(input)
    File(properties.getProperty("sdk.dir"))
        .resolve("platforms")
        .resolve("android-${libs.versions.android.compile.sdk.get()}")
        .resolve("android.jar")
}

dependencies {
    testRuntimeOnly(libs.decoroutinator.mh.invoker)
    testRuntimeOnly(libs.decoroutinator.generator.android)
    testRuntimeOnly(libs.decoroutinator.naive.base.continuation.accessor)
    testRuntimeOnly(files(androidJar))

    testImplementation(kotlin("test"))
    testImplementation(libs.jupiter.api)
    testImplementation(libs.decoroutinator.common)
    testImplementation(libs.coroutines.core.build)
}

configurations.testRuntimeClasspath.get().attributes
    .attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
    .attribute(com.android.build.gradle.internal.attributes.VariantAttr.ATTRIBUTE, objects.named("release"))
    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

dependencies.attributesSchema {
    attribute(KotlinPlatformType.attribute) {
        class AarToJarCompatibilityRule: AttributeCompatibilityRule<KotlinPlatformType> {
            override fun execute(details: CompatibilityCheckDetails<KotlinPlatformType>) {
                val consumerValue = details.consumerValue
                val producerValue = details.producerValue
                if (consumerValue == KotlinPlatformType.jvm && producerValue == KotlinPlatformType.androidJvm) {
                    details.compatible()
                }
            }
        }
        compatibilityRules.add(AarToJarCompatibilityRule::class.java)
    }
}
