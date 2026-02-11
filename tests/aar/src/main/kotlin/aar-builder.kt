@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.tests.aar

import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.intrinsics.ownerClassName
import dev.reformator.bytecodeprocessor.intrinsics.ownerMethodName

@Suppress("unused")
val suspendFunFromAarOwnerClassName = ownerClassName
@Suppress("unused")
val suspendFunFromAarMethodName: String
    get() = _suspendFunFromAarMethodName
@Suppress("unused")
val suspendFunFromAarFileName = currentFileName
@Suppress("unused")
val suspendFunFromAarLineNumber: Int
    get() = _suspendFunFromAarLineNumber

@Suppress("unused")
suspend fun <T> suspendFunFromAar(body: suspend () -> T): T {
    _suspendFunFromAarMethodName = ownerMethodName
    _suspendFunFromAarLineNumber = currentLineNumber + 1
    return body()
}

@Suppress("ObjectPropertyName") private lateinit var _suspendFunFromAarMethodName: String
@Suppress("ObjectPropertyName") private var _suspendFunFromAarLineNumber = 0
