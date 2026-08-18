@file:Suppress("NewApi", "PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.UNKNOWN_LINE_NUMBER
import java.lang.invoke.MethodHandle
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class BaseSpecMethodsFactory: SpecMethodsFactory {
    private val classesByName: MutableMap<String, ClassSpec> = HashMap()
    private val classesByNameUpdateLock = ReentrantLock()

    private fun getClassSpec(specClassName: String): ClassSpec =
        classesByName.optimisticLockGetOrPut(
            key = specClassName,
            lock = classesByNameUpdateLock
        ) { ClassSpec() }

    override fun getSpecMethodHandle(element: StackTraceElement): MethodHandle? {
        SpecMethodsFactoryImpl.getSpecMethodHandle(element)?.let { return it }

        val classSpec = getClassSpec(element.className)

        fun getMethodHandle(): MethodHandle? {
            val methodsByName = classSpec[element.fileName] ?: return null
            val methodSpec = methodsByName[element.methodName] ?: return null
            if (element.normalizedLineNumber !in methodSpec.lineNumbers) return null
            return methodSpec.handle
        }
        getMethodHandle()?.let { return it }

        classSpec.updateLock.withLock {
            getMethodHandle()?.let { return it }

            val lineNumbersByMethod = mutableMapOf<String, MutableSet<Int>>()
            classSpec[element.fileName]?.let { methodsByName ->
                methodsByName.forEach { (methodName, method) ->
                    lineNumbersByMethod[methodName] = method.lineNumbers.toMutableSet()
                }
            }
            lineNumbersByMethod.getOrPut(element.methodName) {
                mutableSetOf(UNKNOWN_LINE_NUMBER)
            }.add(element.normalizedLineNumber)

            classSpec.revision++
            val factoriesByMethod = generateSpecMethodHandles(
                className = element.className,
                classRevision = classSpec.revision,
                fileName = element.fileName,
                lineNumbersByMethod = lineNumbersByMethod
            ) ?: run {
                classSpec.revision--
                return null
            }
            assert { factoriesByMethod.keys == lineNumbersByMethod.keys }

            classSpec[element.fileName] =
                factoriesByMethod.mapValuesCompact(methodsNumberThreshold) { (methodName, handle) ->
                    MethodSpec(
                        handle = handle,
                        lineNumbers = lineNumbersByMethod[methodName]!!.toIntArray()
                    )
                }
        }

        return getMethodHandle()!!
    }

    abstract fun generateSpecMethodHandles(
        className: String,
        classRevision: Int,
        fileName: String?,
        lineNumbersByMethod: Map<String, Set<Int>>
    ): Map<String, MethodHandle>?

    private class MethodSpec(
        val handle: MethodHandle,
        val lineNumbers: IntArray
    )

    private class ClassSpec {
        var revision: Int = -1
        val methodsByFileNameAndMethodName: MutableMap<String?, Map<String, MethodSpec>> = CompactMap()
        val updateLock = ReentrantLock()

        operator fun get(fileName: String?): Map<String, MethodSpec>? =
            try {
                methodsByFileNameAndMethodName[fileName]
            } catch (_: ConcurrentModificationException) {
                updateLock.withLock {
                    methodsByFileNameAndMethodName[fileName]
                }
            }

        operator fun set(fileName: String?, methodsByMethodName: Map<String, MethodSpec>) {
            updateLock.withLock {
                methodsByFileNameAndMethodName[fileName] = methodsByMethodName
            }
        }
    }
}

internal object SpecMethodsFactoryImpl: SpecMethodsFactory {
    private val classSpecsByName: MutableMap<String, ClassSpec> = HashMap()
    private val classSpecsByNameUpdateLock = ReentrantLock()

    private class ClassSpec(
        val fileName: String?,
        val methodsByName: Map<String, MethodSpec>
    ) {
        companion object {
            val notSet = ClassSpec(null, emptyMap())
        }
    }

    private fun getClassSpec(specClassName: String): ClassSpec? =
        classSpecsByName.optimisticLockGet(
            key = specClassName,
            lock = classSpecsByNameUpdateLock,
            notSetValue = ClassSpec.notSet
        )

    override fun getSpecMethodHandle(element: StackTraceElement): MethodHandle? {
        val classSpec = getClassSpec(element.className) ?: return null
        if (classSpec.fileName != element.fileName) return null
        val methodSpec = classSpec.methodsByName[element.methodName] ?: return null
        if (element.normalizedLineNumber !in methodSpec.lineNumbers) return null
        return methodSpec.handle
    }

    init {
        transformedClassesRegistry.addListener(::register)
        classSpecsByNameUpdateLock.withLock {
            transformedClassesRegistry.transformedClasses.forEach(::register)
        }
    }

    private class MethodSpec(
        val lineNumbers: IntArray,
        val handle: MethodHandle
    )

    private fun register(spec: TransformedClassesRegistry.TransformedClassSpec) {
        if (spec.skipSpecMethods) return
        val methodsByName = spec.methods.associateTo(
            if (spec.methods.size < methodsNumberThreshold) {
                CompactMap()
            } else {
                newHashMapForSize(spec.methods.size)
            }
        ) { method ->
            val specMethod = spec.lookup.findStatic(spec.transformedClass, method.realMethodName, specMethodType)
            method.methodName to MethodSpec(
                lineNumbers = method.lineNumbers,
                handle = specMethod
            )
        }
        val classSpec = ClassSpec(
            fileName = spec.fileName,
            methodsByName = methodsByName
        )
        classSpecsByNameUpdateLock.withLock {
            classSpecsByName[spec.className] = classSpec
        }
    }
}
