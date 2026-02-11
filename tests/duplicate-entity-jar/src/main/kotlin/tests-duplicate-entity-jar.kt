package dev.reformator.stacktracedecoroutinator.tests.duplicateentityjar

import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.intrinsics.ownerClassName
import dev.reformator.bytecodeprocessor.intrinsics.ownerMethodName

@Suppress("unused") val duplicateEntityJarTestClassName = ownerClassName
@Suppress("unused") val duplicateEntityJarTestFileName = currentFileName
@Suppress("unused") val duplicateEntityJarTestLineNumber: Int
    get() = _duplicateEntityJarTestLineNumber
@Suppress("unused") val duplicateEntityJarTailCallDeoptimizedTestLineNumber: Int
    get() = _duplicateEntityJarTailCallDeoptimizedTestLineNumber
@Suppress("unused") val duplicateEntityJarTestMethodName: String
    get() = _duplicateEntityJarTestMethodName
@Suppress("unused") val duplicateEntityJarTailCallDeoptimizedTestMethodName: String
    get() = _duplicateEntityJarTailCallDeoptimizedTestMethodName

@Suppress("unused")
suspend fun <T> duplicateEntityJarTest(callIt: suspend () -> T): T {
    _duplicateEntityJarTestMethodName = ownerMethodName
    _duplicateEntityJarTestLineNumber = currentLineNumber + 1
    return callIt()
}

@Suppress("unused")
suspend fun <T> duplicateEntityJarTailCallDeoptimizedTest(callIt: suspend () -> T): T {
    _duplicateEntityJarTailCallDeoptimizedTestMethodName = ownerMethodName
    _duplicateEntityJarTailCallDeoptimizedTestLineNumber = currentLineNumber + 1
    val result = callIt()
    callIt.hashCode() // tail-call deoptimization
    return result
}

@Suppress("ObjectPropertyName") private var _duplicateEntityJarTestLineNumber = 0
@Suppress("ObjectPropertyName") private var _duplicateEntityJarTailCallDeoptimizedTestLineNumber = 0
@Suppress("ObjectPropertyName") private lateinit var _duplicateEntityJarTestMethodName: String
@Suppress("ObjectPropertyName")
private lateinit var _duplicateEntityJarTailCallDeoptimizedTestMethodName: String
