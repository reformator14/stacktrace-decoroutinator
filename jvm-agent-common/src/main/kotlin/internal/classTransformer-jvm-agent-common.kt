@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.bytecodeprocessor.intrinsics.ownerClass
import dev.reformator.stacktracedecoroutinator.classtransformer.internal.ClassBodyTransformationStatus
import dev.reformator.stacktracedecoroutinator.classtransformer.internal.noClassBodyTransformationStatus
import dev.reformator.stacktracedecoroutinator.classtransformer.internal.transformClassBody
import dev.reformator.stacktracedecoroutinator.intrinsics.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
import dev.reformator.stacktracedecoroutinator.provider.providerApiClass
import java.io.ByteArrayInputStream
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.Base64

fun addDecoroutinatorTransformer(inst: Instrumentation) {
    val transformer = DecoroutinatorClassFileTransformer(inst)
    transformer.transform(
        loader = ownerClass.classLoader,
        internalClassName = suspendClassName.internalName,
        classBeingRedefined = null,
        protectionDomain = null,
        classfileBuffer = Base64.getDecoder().decode(suspendClassBodyBase64)
    )
    inst.addTransformer(transformer, inst.isRetransformClassesSupported)
}

private class DecoroutinatorClassFileTransformer(
    private val inst: Instrumentation
): ClassFileTransformer {
    override fun transform(
        loader: ClassLoader?,
        internalClassName: String,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray
    ): ByteArray? =
        transform(
            loader = loader,
            classBeingRedefined = classBeingRedefined,
            classfileBuffer = classfileBuffer
        ).updatedBody


    override fun transform(
        module: Module,
        loader: ClassLoader?,
        internalClassName: String,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray
    ): ByteArray? {
        val transformationStatus = transform(
            loader = loader,
            classBeingRedefined = classBeingRedefined,
            classfileBuffer = classfileBuffer
        )
        if (transformationStatus.needReadProviderModule && inst.isModifiableModule(module)) {
            inst.redefineModule(
                module,
                setOf(providerApiClass.module),
                emptyMap(),
                emptyMap(),
                emptySet(),
                emptyMap()
            )
        }
        return transformationStatus.updatedBody
    }

    private fun transform(
        loader: ClassLoader?,
        classBeingRedefined: Class<*>?,
        classfileBuffer: ByteArray
    ): ClassBodyTransformationStatus {
        if (loader == null || !loader.hasProviderApiDependency) {
            return noClassBodyTransformationStatus
        }

        if (classBeingRedefined != null) {
            val isRedefinitionAllowed = run {
                if (!inst.isRedefineClassesSupported) return@run false
                if (classBeingRedefined.name == BASE_CONTINUATION_CLASS_NAME) {
                    isBaseContinuationRedefinitionAllowed
                } else {
                    isRedefinitionAllowed
                }
            }

            if (!isRedefinitionAllowed) return noClassBodyTransformationStatus
        }

        return transformClassBody(
            classBody = ByteArrayInputStream(classfileBuffer),
            skipSpecMethods = false,
            classBodyResolver = metadataResolver@{ className ->
                val path = "${className.internalName}.class"
                loader.getResourceAsStream(path)
            }
        )
    }
}

private val ClassLoader.hasProviderApiDependency: Boolean
    get() = try {
        loadClass(providerApiClass.name) == providerApiClass
    } catch (_: ClassNotFoundException) {
        false
    }

private val suspendClassName: String
    @LoadConstant("jvmAgentCommonSuspendClassName") get() { fail() }

private val suspendClassBodyBase64: String
    @LoadConstant("jvmAgentCommonSuspendClassBodyBase64") get() { fail() }
