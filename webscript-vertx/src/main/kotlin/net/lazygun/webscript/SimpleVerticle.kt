package net.lazygun.webscript

import io.vertx.core.AbstractVerticle
import io.vertx.core.eventbus.Message
import io.vertx.core.logging.LoggerFactory
import java.util.*

/**
 * @author Ewan
 */
abstract class SimpleVerticle<Incoming, Outgoing> : AbstractVerticle() {

    protected val log = LoggerFactory.getLogger(this.javaClass)!!

    val name = this.javaClass.simpleName

    override fun start() {
        log.info("$name starting")
        vertx.eventBus().consumer(address()) { message: Message<Incoming> ->
            val body = message.body()
            log.debug("Consuming message: $body")
            val response = handleMessage(body)
            log.debug("Replying with message: $response")
            message.reply(response)
        }
    }

    override fun stop() {
        log.info("$name stopping")
    }

    abstract fun address(): String

    abstract fun handleMessage(message: Incoming): Optional<Outgoing>
}