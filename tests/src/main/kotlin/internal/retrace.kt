package dev.reformator.stacktracedecoroutinator.tests.internal

import java.io.File
import kotlin.math.min

class R8Retrace {
    @Suppress("unused")
    constructor(mappingFile: File) {
        mappingFile.bufferedReader().use { reader ->
            classesByObfuscatedName = buildClassesByObfuscatedName(reader.lineSequence().iterator())
        }
    }

    @Suppress("unused")
    constructor(mappingFileLines: Iterator<String>) {
        classesByObfuscatedName = buildClassesByObfuscatedName(mappingFileLines)
    }

    private val classesByObfuscatedName: Map<String, ClassSpec>

    private fun buildClassesByObfuscatedName(mappingFileLines: Iterator<String>) = buildMap {
        var obfuscatedClassName = ""
        var originalClassName = ""
        var fileName: String? = null
        var methods = mutableListOf<MethodSpec>()

        fun flushClass() {
            if (obfuscatedClassName.isNotEmpty() && methods.isNotEmpty()) {
                this[obfuscatedClassName] = ClassSpec(
                    originalName = originalClassName,
                    fileName = fileName,
                    methods = methods
                )
            }
            fileName = null
            methods = mutableListOf()
        }

        mappingFileLines.forEach { line ->
            methodDefinitionRegex.matchEntire(line)?.let { matchResult ->
                val originalLineStart = matchResult.groups[4]!!.value.toInt()
                val originalLineEnd = matchResult.groups[5]?.value?.removePrefix(":")?.toInt() ?: originalLineStart
                methods.add(MethodSpec(
                    obfuscatedName = matchResult.groups[6]!!.value,
                    obfuscatedLineStart = matchResult.groups[1]!!.value.toInt(),
                    obfuscatedLineEnd = matchResult.groups[2]!!.value.toInt(),
                    originalName = matchResult.groups[3]!!.value,
                    originalLineStart = originalLineStart,
                    originalLineEnd = originalLineEnd
                ))
                return@forEach
            }

            if (methods.isEmpty() && fileName == null) {
                sourceFileRegex.matchEntire(line)?.let { matchResult ->
                    fileName = matchResult.groups[1]!!.value
                    return@forEach
                }
            }

            classDefinitionRegex.matchEntire(line)?.let { matchResult ->
                flushClass()
                obfuscatedClassName = matchResult.groups[2]!!.value
                originalClassName = matchResult.groups[1]!!.value
            }
        }

        flushClass()
    }

    fun getPossibleUnobfuscatedFrames(obfuscatedFrame: StackTraceElement): Collection<StackTraceElement> {
        val clazz = classesByObfuscatedName[obfuscatedFrame.className] ?: return emptySet()

        clazz.methods.asSequence()
            .filter { it.obfuscatedName == obfuscatedFrame.methodName }
            .filter { it.obfuscatedLineStart <= obfuscatedFrame.lineNumber && obfuscatedFrame.lineNumber <= it.obfuscatedLineEnd }
            .map { method ->
                StackTraceElement(
                    clazz.originalName,
                    method.originalName,
                    clazz.fileName,
                    min(
                        method.originalLineStart + (obfuscatedFrame.lineNumber - method.obfuscatedLineStart),
                        method.originalLineEnd
                    )
                )
            }
            .toList()
            .let { if (it.isNotEmpty()) return it }

        clazz.methods.asSequence()
            .filter { it.obfuscatedName == obfuscatedFrame.methodName }
            .map { method ->
                StackTraceElement(
                    clazz.originalName,
                    method.originalName,
                    clazz.fileName,
                    -1
                )
            }
            .toList()
            .let { if (it.isNotEmpty()) return it }

        return listOf(StackTraceElement(
            clazz.originalName,
            obfuscatedFrame.methodName,
            clazz.fileName,
            obfuscatedFrame.lineNumber
        ))
    }

    private class ClassSpec(
        val originalName: String,
        val fileName: String?,
        val methods: Collection<MethodSpec>
    )

    private class MethodSpec(
        val obfuscatedName: String,
        val obfuscatedLineStart: Int,
        val obfuscatedLineEnd: Int,
        val originalName: String,
        val originalLineStart: Int,
        val originalLineEnd: Int
    )
}

private const val IDENTIFIER_REGEX = """[^\s\.,#:]+"""
private const val CLASS_NAME_REGEX = """(?:$IDENTIFIER_REGEX\.)*$IDENTIFIER_REGEX"""
private const val PARAMETER_LIST_REGEX = """\((?:$CLASS_NAME_REGEX(?:,$CLASS_NAME_REGEX)*)?\)"""
private const val NUMBER_REGEX = """\d+"""

private val classDefinitionRegex = Regex("""($CLASS_NAME_REGEX) -> ($CLASS_NAME_REGEX):""")
private val sourceFileRegex = Regex("""# \{"id":"sourceFile","fileName":"(.+)"\}""")
private val methodDefinitionRegex = Regex(""" {4}($NUMBER_REGEX):($NUMBER_REGEX):$CLASS_NAME_REGEX ($IDENTIFIER_REGEX)$PARAMETER_LIST_REGEX:($NUMBER_REGEX)(:$NUMBER_REGEX)? -> ($IDENTIFIER_REGEX)""")
