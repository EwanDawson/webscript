package net.lazygun.webscript

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import java.util.*

val numRunners = 1

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("Application")
    val vertx = Vertx.vertx()

//    vertx.deployVerticle(Invoker()) { result ->
//        if (result.succeeded()) {
//            log.info("Successfully Deployed Invoker Verticle: ${result.result()}")
//        }
//        else {
//            log.error("Failed to deploy Invoker Verticle: ${result.result()}")
//        }
//    }
//
//    val latch = CountDownLatch(numRunners)
//    val script = "invoke('myscript', payload).thenApply { result(it) }"
//    for (i in 1..numRunners) {
//        vertx.deployVerticle(GroovyScriptRunner()) { event ->
//            if (event.succeeded()) {
//                val message = GroovyScriptAndPayload(script, listOf(i)).toJsonObject()
//                vertx.eventBus().send(GroovyScriptRunner.address, message) { result: AsyncResult<Message<Any?>> ->
//                    if (result.succeeded()) {
//                        log.info("Success: runner $i replied with: ${result.result()?.body()}")
//                    } else {
//                        log.error("Failure sending message to runner $i: ${result.cause()}")
//                    }
//                    latch.countDown()
//                }
//            } else {
//                latch.countDown()
//            }
//        }
//    }
//    latch.await()
//    Thread.sleep(5000)

    fun <T> send(address: String, message: JsonAware): Future<Message<T>> {
        val future = Future.future<Message<T>>()
        vertx.eventBus().send(address, message.toJson(), future.completer())
        return future
    }

    val start = Future.future<String>()
    val finish = Future.future<Void>()
    vertx.deployVerticle(Binder(), start.completer())
    start.compose { id ->
        log.info("Verticle $id deployed. Sending message")
        send<UUID>(AddBindingMessage.address, AddBindingMessage(UUID.randomUUID(), "me", "you"))
    }.compose { uuid ->
        log.info("Got uuid $uuid. Binding identifier")
        send<String>(BindIdentifierMessage.address, BindIdentifierMessage(uuid.body(), "me"))
    }.compose({ binding: Message<String> ->
        log.info("Bound identifier to $binding")
        println(binding.body())
        finish.complete()
    }, finish)
}

interface JsonAware {
    fun toJson(): JsonObject
}