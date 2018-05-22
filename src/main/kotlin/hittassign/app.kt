package hittassign

import kotlinx.coroutines.experimental.*
import kotlin.system.exitProcess

fun getGreeting(): String {
    return "Hello, world!"
}

suspend fun executeApp(): Int {
    delay(1000L)

    return 42
}

fun main(args: Array<String>) {
    runBlocking {
        val result = async { executeApp() }

        println("done. ${result.await()}")
        exitProcess(if (result.await() > 0) 0 else 1)
    }
}
