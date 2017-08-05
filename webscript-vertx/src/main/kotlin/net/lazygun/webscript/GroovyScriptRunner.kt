package net.lazygun.webscript

import groovy.lang.Binding
import groovy.lang.GroovyShell
import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.codehaus.groovy.runtime.MethodClosure
import java.util.concurrent.CompletableFuture
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.vertx.core.logging.LoggerFactory

class GroovyScriptRunner : AbstractVerticle() {

    val log = LoggerFactory.getLogger(this::class.java)!!

    override fun start() {
        log.info("Groovy Script Runner starting")
        vertx.eventBus().consumer(address) { message : Message<JsonObject> ->
            log.info("Received ${message.body()}")
            val result = run(GroovyScriptAndPayload.fromJsonObject(message.body()))
            if (result != null) {
                message.reply(prepareReply(result))
            }
        }
    }

    private fun prepareReply(result: Any?): Any? {
        return when (result) {
            is Boolean, is Number, is String -> result
            is CharSequence -> result.toString()
            is Iterable<*> -> {
                val array = JsonArray()
                result.forEach { e -> array.add(prepareReply(e)) }
            }
            is Iterator<*> -> {
                val array = JsonArray()
                result.forEach { e -> array.add(prepareReply(e)) }
            }
            else -> JsonObject.mapFrom(result)
        }
    }

    override fun stop() {
        log.info("Groovy Script Runner stopping")
    }

    private fun run(scriptAndPayload: GroovyScriptAndPayload) : Any? {
        val binding = Binding()
        binding.setVariable("payload", scriptAndPayload.payload)
        val methodClosure = MethodClosure(this, "invoke")
        binding.setVariable("invoke", methodClosure)
        val shell = GroovyShell(binding)
        val result = shell.evaluate(scriptAndPayload.script)
        return result
    }

    @Suppress("unused")
    private fun invoke(identifier: String, payload: List<Any>): CompletableFuture<Any> {
        val message = JsonObject.mapFrom(mapOf("identifier" to identifier, "payload" to payload))
        val future = CompletableFuture<Any>()
        log.info("Sending message: ${message.encode()} to address: ${Invoker.address}")
        vertx.eventBus().send(Invoker.address, message) { event: AsyncResult<Message<Any>> ->
            if (event.succeeded()) {
                val result = event.result().body()
                log.info("Message to ${Invoker.address} succeeded with result: $result")
                val completion = future.complete(result)
                log.info(if (completion) "Completed" else "Could not complete!")
            }
            else {
                log.info("Message tp ${Invoker.address} failed with exception: ${event.cause().message}")
                future.completeExceptionally(event.cause())
            }
        }
        return future
    }

    companion object {
        val address = "script.groovy.run"
    }
}

data class GroovyScriptAndPayload
@JsonCreator constructor(@JsonProperty("script") val script: String,
                         @JsonProperty("payload") val payload: List<Any>) {
    fun toJsonObject() = JsonObject.mapFrom(this)!!
    companion object Factory {
        fun fromJsonObject(jsonObject: JsonObject)
            = jsonObject.mapTo(GroovyScriptAndPayload::class.java)!!
    }
}