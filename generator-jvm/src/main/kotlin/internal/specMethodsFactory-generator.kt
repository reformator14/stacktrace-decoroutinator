@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generatorjvm.internal

import dev.reformator.stacktracedecoroutinator.provider.internal.*
import java.lang.invoke.MethodHandle
import java.util.concurrent.CopyOnWriteArrayList

internal class GeneratorJvmSpecMethodsFactory: BaseSpecMethodsFactory() {
    init {
        //assert the platform
        DecoroutinatorClassLoader().buildClassAndGetSpecHandlesByMethod(
            className = "stub",
            fileName = "unknown",
            lineNumbersByMethod = emptyMap()
        )
    }

    override fun generateSpecMethodHandles(
        className: String,
        classRevision: Int,
        fileName: String?,
        lineNumbersByMethod: Map<String, Set<Int>>
    ): Map<String, MethodHandle> {
        while (loadersByRevision.size <= classRevision) loadersByRevision.add(DecoroutinatorClassLoader())
        val loader = loadersByRevision[classRevision]
        return loader.buildClassAndGetSpecHandlesByMethod(
            className = className,
            fileName = fileName,
            lineNumbersByMethod = lineNumbersByMethod
        )
    }

    private val loadersByRevision: MutableList<DecoroutinatorClassLoader> = CopyOnWriteArrayList()
}