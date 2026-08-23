@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generatorjvm.internal

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.internal.TransformationMetadata
import dev.reformator.stacktracedecoroutinator.common.internal.AnnotationMetadataResolver
import dev.reformator.stacktracedecoroutinator.common.internal.KotlinDebugMetadata
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.decoroutinatorSpecMethodAnnotation
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.decoroutinatorTransformedAnnotation
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.getClassNode
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.getField
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.kotlinDebugMetadataAnnotation
import java.io.InputStream

class AnnotationMetadataResolverImpl: AnnotationMetadataResolver {
    @Suppress("UNCHECKED_CAST")
    override fun getTransformationMetadata(classBody: InputStream): TransformationMetadata? {
        val clazz = getClassNode(classBody, skipCode = true) ?: return null
        val transformedAnnotation = clazz.decoroutinatorTransformedAnnotation ?: return null
        val fileNamePresent = transformedAnnotation.getField(decoroutinatorTransformedFileNamePresentMethodName) as Boolean?
        val fileName = transformedAnnotation.getField(decoroutinatorTransformedFileNameMethodName) as String?
        val className = transformedAnnotation.getField(decoroutinatorTransformedClassNameMethodName) as String?
        val methods = clazz.methods.orEmpty().mapNotNull { methodNode ->
            methodNode.decoroutinatorSpecMethodAnnotation?.let { specMethodAnnotation ->
                TransformationMetadata.Method(
                    name = specMethodAnnotation.getField(decoroutinatorSpecMethodMethodNameMethodName) as String,
                    realName = methodNode.name,
                    lineNumbers = (specMethodAnnotation.getField(decoroutinatorSpecMethodLineNumbersMethodName) as List<Int>).toIntArray()
                )
            }
        }
        return TransformationMetadata(
            className = className.orEmpty(),
            fileName = if (fileNamePresent == null || fileNamePresent) fileName else null,
            methods = methods
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun getKotlinDebugMetadata(classBody: InputStream): KotlinDebugMetadata? {
        val clazz = getClassNode(classBody, skipCode = true) ?: return null
        val metadataAnnotation = clazz.kotlinDebugMetadataAnnotation ?: return null
        return KotlinDebugMetadata(
            sourceFile = metadataAnnotation.getField(debugMetadataFileNameMethodName) as String,
            className = metadataAnnotation.getField(debugMetadataClassNameMethodName) as String,
            methodName = metadataAnnotation.getField(debugMetadataMethodNameMethodName) as String,
            lineNumbers = (metadataAnnotation.getField(debugMetadataLineNumbersMethodName) as List<Int>).toIntArray(),
        )
    }
}

private val decoroutinatorTransformedFileNamePresentMethodName: String
    @LoadConstant("decoroutinatorTransformedFileNamePresentMethodName") get() = fail()

private val decoroutinatorTransformedFileNameMethodName: String
    @LoadConstant("decoroutinatorTransformedFileNameMethodName") get() = fail()

private val decoroutinatorTransformedClassNameMethodName: String
    @LoadConstant("decoroutinatorTransformedClassNameMethodName") get() = fail()

private val decoroutinatorSpecMethodMethodNameMethodName: String
    @LoadConstant("decoroutinatorSpecMethodMethodNameMethodName") get() = fail()

private val decoroutinatorSpecMethodLineNumbersMethodName: String
    @LoadConstant("decoroutinatorSpecMethodLineNumbersMethodName") get() = fail()

private val debugMetadataFileNameMethodName: String
    @LoadConstant("debugMetadataFileNameMethodName") get() = fail()

private val debugMetadataLineNumbersMethodName: String
    @LoadConstant("debugMetadataLineNumbersMethodName") get() = fail()

private val debugMetadataMethodNameMethodName: String
    @LoadConstant("debugMetadataMethodNameMethodName") get() = fail()

private val debugMetadataClassNameMethodName: String
    @LoadConstant("debugMetadataClassNameMethodName") get() = fail()
