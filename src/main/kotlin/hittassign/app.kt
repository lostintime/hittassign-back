package hittassign

import com.github.kittinunf.result.flatMap
import hittassign.dsl.lex
import hittassign.dsl.parse
import hittassign.runtime.RuntimeContext
import hittassign.runtime.execute
import kotlinx.coroutines.experimental.*


fun main(args: Array<String>) = runBlocking {
    val job = launch {
        lex("").flatMap { parse(it) }.fold({
            execute(it, RuntimeContext()).fold({
                println("success")
            }, {
                println("Runtime failure: $it")
            })
        }, {
            println("Parse failure: $it")
        })
    }

    job.join()
    println("done.")
//    exitProcess(if (job.await() > 0) 0 else 1)
}
