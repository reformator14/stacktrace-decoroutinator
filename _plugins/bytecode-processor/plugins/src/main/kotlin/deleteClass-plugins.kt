@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.DeleteClass
import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContext
import dev.reformator.bytecodeprocessor.api.ProcessingDirectory
import dev.reformator.bytecodeprocessor.api.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.find

object DeleteClassProcessor: Processor {
    override fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext) {
        directory.classes.forEach { processingClass ->
            processingClass.node.invisibleAnnotations.find(DeleteClass::class.java) ?: return@forEach
            processingClass.delete()
        }
    }
}
