package net.lazygun.webscript

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * @author Ewan
 */
class Binder : AbstractVerticle() {

    val log = LoggerFactory.getLogger(this.javaClass)!!

    val name = this.javaClass.simpleName!!

    val cache = mutableMapOf<UUID, Map<String, String>>()

    override fun start(startFuture: Future<Void>?) {
        log.info("$name starting")
        val fs = vertx.fileSystem()
        consumeBindIdentifierMessages()
        consumeAddBindingMessages()
        consumeRemoveBindingMessages()
    }

    fun consumeRemoveBindingMessages(): Unit {
        vertx.eventBus().consumer<JsonObject>(RemoveBindingMessage.address) { event ->
            log.info("Received event: ${event.body()}")
            val (bindingsId, identifier) = RemoveBindingMessage.fromJson(event.body()!!)
            val reply = Future.future<Any>()
            loadBindings(bindingsId)
                .compose { bindings -> Future.succeededFuture(bindings - identifier) }
                .compose { newBindings -> saveBindings(newBindings) }
                .compose({ newBindingsId ->
                    event.reply(newBindingsId)
                    reply.complete()
                }, reply)
        }
    }

    fun consumeAddBindingMessages(): Unit {
        vertx.eventBus().consumer<JsonObject>(AddBindingMessage.address) { event ->
            log.info("Received event: ${event.body()}")
            val (bindingsId, from, to) = AddBindingMessage.fromJson(event.body()!!)
            val reply = Future.future<Message<Any>>()
            loadBindings(bindingsId)
                .compose { bindings -> saveBindings(bindings + Pair(from, to)) }
                .compose({ newBindingsId -> event.reply(newBindingsId, reply.completer()) }, reply)
        }
    }

    fun consumeBindIdentifierMessages(): Unit {
        vertx.eventBus().consumer<JsonObject>(BindIdentifierMessage.address) { event ->
            log.info("Received event: ${event.body()}")
            val (bindingsId, identifier) = BindIdentifierMessage.fromJson(event.body()!!)
                val reply = Future . future < Any >()
            loadBindings(bindingsId).compose({ bindings ->
                event.reply(bindings[identifier])
                reply.complete()
            }, reply)
        }
    }

    fun saveBindings(newBindings: Map<String, String>): Future<UUID> {
        val newBindingsId = UUID.randomUUID()
        cache[newBindingsId] = newBindings
        val fs = vertx.fileSystem()
        val path = bindingsPath(newBindingsId)
        val writeFile = Future.future<Void>()
        val props = Properties()
        props.putAll(newBindings)
        val bytes = ByteArrayOutputStream()
        props.store(bytes, null)
        fs.writeFile(path, Buffer.buffer(bytes.toByteArray()), writeFile.completer())
        return writeFile.compose { _ -> Future.succeededFuture(newBindingsId) }
    }

    private fun bindingsPath(bindingsId: UUID?) = "bindings_$bindingsId.properties"

    fun loadBindings(id: UUID): Future<Map<String, String>> {
        log.info("Loading bindings with uuid $id")
        if (cache.containsKey(id)) return Future.succeededFuture(cache[id])
        val fs = vertx.fileSystem()
        val path = bindingsPath(id)
        val readFile = Future.future<Buffer>()
        fs.readFile(path, readFile.completer())
        return readFile.compose { buffer ->
            val propsMap = mutableMapOf<String, String>()
            val props = Properties()
            props.load(ByteArrayInputStream(buffer.bytes))
            props.stringPropertyNames().forEach { key -> propsMap.put(key, props.getProperty(key)) }
            val bindings = propsMap.toMap()
            cache[id] = bindings.withDefault { identifier -> identifier }
            Future.succeededFuture(bindings)
        }
    }

    override fun stop() {
        log.info("$name stopping")
    }
}

class UnknownBindingIdException : Throwable()

data class BindIdentifierMessage @JsonCreator constructor(
    @JsonProperty("bindingsId") val bindingsId: UUID,
    @JsonProperty("identifier") val identifier: String): JsonAware {
    override fun toJson() = JsonObject.mapFrom(this)!!
    companion object {
        val address = "binder.bindIdentifier"
        fun fromJson(json: JsonObject) = json.mapTo(BindIdentifierMessage::class.java)!!
    }
}

data class AddBindingMessage @JsonCreator constructor(
    @JsonProperty("bindingsId") val bindingsId: UUID,
    @JsonProperty("from") val from: String,
    @JsonProperty("to") val to: String): JsonAware {
    override fun toJson() = JsonObject.mapFrom(this)!!
    companion object {
        val address = "binder.addBinding"
        fun fromJson(json: JsonObject) = json.mapTo(AddBindingMessage::class.java)!!
    }
}

data class RemoveBindingMessage @JsonCreator constructor(
    @JsonProperty("bindingsId") val bindingsId: UUID,
    @JsonProperty("identifier") val identifier: String): JsonAware {
    override fun toJson() = JsonObject.mapFrom(this)!!
    companion object {
        val address = "binder.removeBinding"
        fun fromJson(json: JsonObject) = json.mapTo(RemoveBindingMessage::class.java)!!
    }
}