import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

object Test {
    suspend fun rec(depth: Int) {
        if (depth == 0) {
            yield()
            throw Exception("exception at ${System.currentTimeMillis()}")
        }
        rec(depth - 1)
    }
}

fun main() {
    DecoroutinatorJvmApi.install() // enable stacktrace-decoroutinator runtime
    try {
        runBlocking {
            Test.rec(10)
        }
    } catch (e: Exception) {
        e.printStackTrace() // print full stack trace with 10 recursive calls
    }
}
