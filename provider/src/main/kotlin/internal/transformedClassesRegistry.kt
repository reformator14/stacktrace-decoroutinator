@file:Suppress("NewApi", "PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpecMethod
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import java.lang.invoke.MethodHandles
import java.lang.reflect.GenericSignatureFormatError
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal class TransformedClassesRegistryImpl: TransformedClassesRegistry {
    private val _transformedClasses: MutableMap<Class<*>, TransformedClassesRegistry.TransformedClassSpec> =
        ConcurrentHashMap()
    private val listeners: MutableList<TransformedClassesRegistry.Listener> = CopyOnWriteArrayList()

    override val transformedClasses: Collection<TransformedClassesRegistry.TransformedClassSpec>
        get() {
            while (true) {
                try {
                    return ArrayList(_transformedClasses.values)
                } catch (_: ConcurrentModificationException) { }
            }
        }

    override fun get(clazz: Class<*>): TransformedClassesRegistry.TransformedClassSpec? =
        _transformedClasses[clazz]

    override fun addListener(listener: TransformedClassesRegistry.Listener) {
        listeners.add(listener)
    }

    override fun registerTransformedClass(lookup: MethodHandles.Lookup) {
        val clazz: Class<*> = lookup.lookupClass()
        val loader = clazz.classLoader ?: ClassLoader.getSystemClassLoader()
        val meta = try {
            clazz.getDeclaredAnnotation(DecoroutinatorTransformed::class.java)?.let { transformedAnnotation ->
                TransformationMetadata(
                    className = transformedAnnotation.className,
                    fileName = if (transformedAnnotation.fileNamePresent) transformedAnnotation.fileName else null,
                    methods = clazz.declaredMethods.mapNotNull { method ->
                        method.getDeclaredAnnotation(DecoroutinatorSpecMethod::class.java)?.let { specMethodAnnotation ->
                            TransformationMetadata.Method(
                                name = specMethodAnnotation.methodName,
                                realName = method.name,
                                lineNumbers = specMethodAnnotation.lineNumbers
                            )
                        }
                    }
                )
            }
        // https://youtrack.jetbrains.com/issue/KT-25337
        } catch (_: GenericSignatureFormatError) {
            if (annotationMetadataResolver != null) {
                try {
                    clazz.getBodyStream(loader)?.use { body ->
                        annotationMetadataResolver.getTransformationMetadata(body)
                    }
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }
        if (meta != null) {
            val transformedClassSpec = TransformedClassesRegistry.TransformedClassSpec(
                transformedClass = clazz,
                className = meta.className,
                fileName = meta.fileName,
                lookup = lookup,
                methods = meta.methods.map {
                    TransformedClassesRegistry.TransformedClassSpec.Method(
                        methodName = it.name,
                        realMethodName = it.realName,
                        lineNumbers = it.lineNumbers
                    )
                }
            )
            _transformedClasses[clazz] = transformedClassSpec
            callListeners(transformedClassSpec)
        }
    }

    private fun callListeners(spec: TransformedClassesRegistry.TransformedClassSpec) {
        listeners.forEach { listener ->
            try {
                listener.onNewTransformedClass(spec)
            } catch (exception: Throwable) {
                try {
                    listener.onException(exception)
                } catch (_: Throwable) {}
            }
        }
    }
}
