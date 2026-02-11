@file:Suppress("PackageDirectoryMismatch")

package org.gradle.kotlin.dsl

import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.gradleplugin.BytecodeProcessorPluginExtension
import dev.reformator.bytecodeprocessor.gradleplugin.EXTENSION_NAME
import dev.reformator.bytecodeprocessor.gradleplugin.INIT_TASK_NAME
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import java.net.URLClassLoader

fun Project.bytecodeProcessor(configure: BytecodeProcessorPluginExtension.() -> Unit) {
    extensions.configure(EXTENSION_NAME, configure)
}

val Project.bytecodeProcessor: BytecodeProcessorPluginExtension
    get() = extensions.getByName(EXTENSION_NAME) as BytecodeProcessorPluginExtension

val Project.bytecodeProcessorInitTask: Task
    get() = tasks.findByName(INIT_TASK_NAME)!!

fun Project.bytecodeProcessorFrom(configuration: Configuration, processorClassName: String): Processor {
    bytecodeProcessorInitTask.dependsOn(configuration)
    val loadedProcessor: Processor by lazy {
        val urls = configuration.resolve().map { it.toURI().toURL() }.toTypedArray()
        val processorClass = URLClassLoader(urls, Processor::class.java.classLoader)
            .loadClass(processorClassName)
        processorClass.getConstructor().newInstance() as Processor
    }
    return Processor { directory, context ->
        loadedProcessor.process(directory, context)
    }
}
