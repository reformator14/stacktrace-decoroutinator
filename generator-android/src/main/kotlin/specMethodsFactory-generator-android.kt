@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("SpecMethodsRegistryGeneratorAndroidKt")

package dev.reformator.stacktracedecoroutinator.generatorandroid

import android.util.Log
import com.android.dex.DexFormat
import com.android.dx.dex.DexOptions
import com.android.dx.dex.file.ClassDefItem
import com.android.dx.dex.file.DexFile
import com.android.dx.rop.cst.CstString
import com.android.dx.rop.cst.CstType
import com.android.dx.rop.type.StdTypeList
import com.android.dx.rop.type.Type
import dalvik.system.InMemoryDexClassLoader
import dev.reformator.stacktracedecoroutinator.common.internal.*
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
import dev.reformator.stacktracedecoroutinator.runtimesettings.internal.getRuntimeSettingsValue
import java.io.IOException
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.ByteBuffer

private val androidGeneratorAttemptsCount =
    getRuntimeSettingsValue({ androidGeneratorAttemptsCount }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.androidGeneratorAttemptsCount",
            "3"
        )!!.toInt()
    }

private const val LOG_TAG = "Decoroutinator"

internal class AndroidSpecMethodsFactory: BaseSpecMethodsFactory() {
    init {
        //assert the platform
        generateSpecMethodHandles(
            className = "stub",
            classRevision = 0,
            fileName = "unknown",
            lineNumbersByMethod = emptyMap()
        )
    }

    override fun generateSpecMethodHandles(
        className: String,
        classRevision: Int,
        fileName: String?,
        lineNumbersByMethod: Map<String, Set<Int>>
    ): Map<String, MethodHandle>? {
        repeat(androidGeneratorAttemptsCount) {
            val loader = try {
                buildClassLoader(
                    className = className,
                    fileName = fileName,
                    lineNumbersByMethod = lineNumbersByMethod
                )
            // https://github.com/reformator14/stacktrace-decoroutinator/issues/42#issuecomment-2508562491
            } catch (e: IOException) {
                Log.w(LOG_TAG, e)
                return@repeat
            }
            val clazz = try {
                @Suppress("NewApi")
                loader.findClass(className)
            // https://github.com/reformator14/stacktrace-decoroutinator/issues/42#issuecomment-2508562491
            } catch (e: ClassNotFoundException) {
                Log.w(LOG_TAG, e)
                return@repeat
            }
            val result = run {
                @Suppress("NewApi") val lookup = MethodHandles.publicLookup()
                lineNumbersByMethod.mapValues { (methodName, _) ->
                    val handle = try {
                        @Suppress("NewApi") lookup.findStatic(clazz, methodName, specMethodType)
                    // https://github.com/reformator14/stacktrace-decoroutinator/issues/30#issuecomment-2346066638
                    } catch (e: NoSuchMethodException) {
                        Log.w(LOG_TAG, e)
                        return@repeat
                    // https://github.com/reformator14/stacktrace-decoroutinator/issues/39#issuecomment-2421913959
                    } catch (e: IllegalAccessException) {
                        Log.w(LOG_TAG, e)
                        return@repeat
                    }

                    // https://issuetracker.google.com/issues/366474683
                    @Suppress("NewApi") lookup.revealDirect(handle)

                    handle
                }
            }
            return result
        }
        Log.w(LOG_TAG, "Failed to generate spec methods for class [$className] after $androidGeneratorAttemptsCount attempts")
        return null
    }
}

private fun buildClassLoader(
    className: String,
    fileName: String?,
    lineNumbersByMethod: Map<String, Set<Int>>
): InMemoryDexClassLoader {
    val fileNameCstString = fileName?.let { CstString(it) }
    val dexOptions = DexOptions()
    dexOptions.minSdkVersion = DexFormat.API_METHOD_HANDLES
    val dexFile = DexFile(dexOptions)
    val clazzType = CstType(Type.internClassName(className.internalName))
    val classDef = ClassDefItem(
        clazzType,
        Modifier.PUBLIC or Modifier.FINAL,
        CstType.OBJECT,
        StdTypeList.EMPTY,
        fileNameCstString
    )
    lineNumbersByMethod.forEach { (methodName, lineNumbers) ->
        classDef.addDirectMethod(buildSpecMethod(
            dexOptions = dexOptions,
            clazz = clazzType,
            fileName = fileNameCstString,
            methodName = methodName,
            lineNumbers = lineNumbers
        ))
    }
    dexFile.add(classDef)
    @Suppress("NewApi")
    return InMemoryDexClassLoader(
        ByteBuffer.wrap(dexFile.toDex(null, false)),
        DecoroutinatorSpec::class.java.classLoader
    )
}

private val classLoaderFindClassMethod: Method =
    ClassLoader::class.java.getDeclaredMethod(
        ClassLoader::findClass.name,
        String::class.java
    ).also {
        it.isAccessible = true
    }

private fun ClassLoader.findClass(className: String): Class<*>? =
    classLoaderFindClassMethod.invoke(this, className) as Class<*>?
