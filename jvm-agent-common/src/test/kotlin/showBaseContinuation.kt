@file:Suppress("PackageDirectoryMismatch")

package showbasecontinuation

import dev.reformator.stacktracedecoroutinator.intrinsics.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.getResourceAsStream
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter

fun main() {
    val classReader = ClassReader(null.getResourceAsStream(BASE_CONTINUATION_CLASS_NAME.internalName + ".class"))
    PrintWriter(System.out.writer()).use {
        classReader.accept(TraceClassVisitor(it), 0)
    }
}
