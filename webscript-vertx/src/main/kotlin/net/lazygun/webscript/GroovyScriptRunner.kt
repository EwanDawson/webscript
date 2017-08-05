package net.lazygun.webscript

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.lang.Binding
import groovy.lang.GroovyShell
import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import me.escoffier.vertx.completablefuture.VertxCompletableFuture
import org.codehaus.groovy.runtime.MethodClosure
import java.util.concurrent.CompletableFuture

class GroovyScriptRunner : AbstractVerticle() {

    val log = LoggerFactory.getLogger(this::class.java)!!

    override fun start() {
        log.info("Groovy Script Runner starting")
        vertx.eventBus().consumer(address) { message : Message<JsonObject> ->
            log.info("Received ${message.body()}")
            val result = VertxCompletableFuture<Any?>(vertx)
            run(GroovyScriptAndPayload.fromJsonObject(message.body()), result)
            result.thenApply { r -> message.reply(prepareReply(r)) }
        }
    }

    private fun prepareReply(result: Any?): Any? {
        return when (result) {
            null -> result
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

    private fun run(scriptAndPayload: GroovyScriptAndPayload, result: CompletableFuture<Any?>): Unit {
        val binding = Binding()
        binding.setVariable("payload", scriptAndPayload.payload)
        for (method in listOf("invoke", "anyOf", "allOf")) {
            val methodClosure = MethodClosure(this, method)
            binding.setVariable(method, methodClosure)
        }
        val resultMethodClosure = MethodClosure(this, "result").curry(result)
        binding.setVariable("result", resultMethodClosure)
        val shell = GroovyShell(binding)
        try {
            shell.evaluate(scriptAndPayload.script)
        } catch(e: Exception) {
            result.completeExceptionally(e)
        }
    }

    @Suppress("unused")
    private fun invoke(identifier: String, payload: List<Any>): CompletableFuture<Any?> {
        val message = JsonObject.mapFrom(mapOf("identifier" to identifier, "payload" to payload))
        log.info("Sending message: ${message.encode()} to address: ${Invoker.address}")
        val future = VertxCompletableFuture<Any?>(vertx)
        vertx.eventBus().send(Invoker.address, message) { event: AsyncResult<Message<Any>> ->
            if (event.succeeded()) {
                val result = event.result().body()
                log.info("Message to ${Invoker.address} succeeded with result: $result")
                future.complete(result)
            }
            else {
                val exception = event.cause()
                log.info("Message tp ${Invoker.address} failed with exception: ${exception.message}")
                future.completeExceptionally(exception)
            }
        }
        return future
    }

    @Suppress("unused")
    private fun result(future: CompletableFuture<Any?>, value: Any?): Unit {
        future.complete(value)
    }

    @Suppress("unused")
    private fun allOf(vararg futures: CompletableFuture<*>): CompletableFuture<Void> {
        return VertxCompletableFuture.allOf(vertx, *futures)
    }

    @Suppress("unused")
    private fun anyOf(vararg futures: CompletableFuture<*>): CompletableFuture<*> {
        return VertxCompletableFuture.anyOf(vertx, *futures)
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