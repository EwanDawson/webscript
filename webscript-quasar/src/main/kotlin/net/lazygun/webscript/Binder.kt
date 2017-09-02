package net.lazygun.webscript

import co.paralleluniverse.actors.behaviors.ProxyServerActor
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.kotlin.Actor
import java.util.*

class BinderActor : Actor() {
    override fun doRun(): Any? {
        receive { msg ->
            when (msg) {
                is NewBindings -> msg.
            }
        }
    }
}

@Suspendable
interface Binder {
    fun bind(name: String): String
    fun addBindings(bindings: Bindings): BindingsId
    fun removeBindings(names: Set<String>): BindingsId
    companion object {
        fun create(): Binder = ProxyServerActor(false, BinderImpl())
    }
}

private class BinderImpl : Binder {
    override fun bind(name: String): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addBindings(bindings: Bindings): BindingsId {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeBindings(names: Set<String>): BindingsId {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

private class BinderServerActor(val proxy: ProxyServerActor) : Binder {
    val server = proxy.spawn()
    override fun bind(name: String): String {

    }

    override fun addBindings(bindings: Bindings): BindingsId {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeBindings(names: Set<String>): BindingsId {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

data class BindingsId(val value: UUID = UUID.randomUUID())

data class BindMessage(val bindingsId: BindingsId, val value: String)

typealias Bindings = Map<String, String>

data class NewBindingsMessage(val bindings: Bindings)

data class AddBindingsMessage(val bindingsId: BindingsId, val bindings: Bindings)

data class RemoveBindingsMessage(val bindingsId: BindingsId, val names: Set<String>)