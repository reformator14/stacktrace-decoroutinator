@file:Suppress("PackageDirectoryMismatch")
@file:ChangeClassName(
    toName = "dev.reformator.stacktracedecoroutinator.gradleplugin.GroovyDslInitializer",
    deleteAfterChanging = true
)

package dev.reformator.stacktracedecoroutinator.gradleplugin

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.intrinsics.fail
import org.gradle.api.Project

internal fun initGroovyDsl(@Suppress("unused") target: Project): Unit = fail()
