package net.lazygun.webscript

import io.vertx.core.AsyncResult
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.logging.LoggerFactory
import java.util.concurrent.CountDownLatch

val numRunners = 1

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("Application")
    val vertx = Vertx.vertx()

    vertx.deployVerticle(Invoker()) { result ->
        if (result.succeeded()) {
            log.info("Successfully Deployed Invoker Verticle: ${result.result()}")
        }
        else {
            log.error("Failed to deploy Invoker Verticle: ${result.result()}")
        }
    }

    val latch = CountDownLatch(numRunners)
    val script = "invoke('myscript', payload) { println it }"
    for (i in 1..numRunners) {
        vertx.deployVerticle(GroovyScriptRunner()) { event ->
            if (event.succeeded()) {
                val message = GroovyScriptAndPayload(script, listOf(i)).toJsonObject()
                vertx.eventBus().send(GroovyScriptRunner.address, message) { result: AsyncResult<Message<Any?>> ->
                    if (result.succeeded()) {
                        log.info("Success: runner $i replied with: ${result.result()?.body()}")
                    } else {
                        log.error("Failure sending message to runner $i: ${result.cause()}")
                    }
                    latch.countDown()
                }
            } else {
                latch.countDown()
            }
        }
    }
    latch.await()
    Thread.sleep(5000)
    vertx.close()
}

