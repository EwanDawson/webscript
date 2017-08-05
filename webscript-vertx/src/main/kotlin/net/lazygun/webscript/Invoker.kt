package net.lazygun.webscript

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.vertx.core.AbstractVerticle
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory

class Invoker : AbstractVerticle() {

    val log = LoggerFactory.getLogger(this::class.java)!!

    override fun start() {
        log.info("Invoker starting")
        vertx.eventBus().consumer(address) { message: Message<JsonObject> ->
            log.info("Received message: ${message.body()}, sent to: $address")
            val invokerMessage = InvokerMessage.fromJsonObject(message.body())
            message.reply("Handled invocation: ${invokerMessage.toJsonObject().encode()}")
        }
    }

    override fun stop() {
        log.info("Invoker stopping")
    }

    companion object {
        val address = "script.invoke"
    }
}

data class InvokerMessage
@JsonCreator constructor(@JsonProperty("identifier") val identifier: String,
                         @JsonProperty("payload") val payload: List<Any>) {
    fun toJsonObject() = JsonObject.mapFrom(this)!!
    companion object Factory {
        fun fromJsonObject(jsonObject: JsonObject)
            = jsonObject.mapTo(InvokerMessage::class.java)!!
    }
}