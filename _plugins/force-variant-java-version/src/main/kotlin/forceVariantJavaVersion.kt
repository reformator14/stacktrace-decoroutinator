@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.forcevariantjavaversion

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE

@Suppress("unused")
class ForceVariantJavaVersionPlugin: Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        afterEvaluate { _ ->
            configurations.forEach { conf ->
                if (conf.attributes.getAttribute(TARGET_JVM_VERSION_ATTRIBUTE) == 9) {
                    conf.attributes.attribute(TARGET_JVM_VERSION_ATTRIBUTE, 8)
                }
                conf.outgoing.variants.forEach { variant ->
                    if (variant.attributes.getAttribute(TARGET_JVM_VERSION_ATTRIBUTE) == 9) {
                        variant.attributes.attribute(TARGET_JVM_VERSION_ATTRIBUTE, 8)
                    }
                }
            }
        }
    }
}
