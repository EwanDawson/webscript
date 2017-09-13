import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import us.bpsm.edn.Tag
import us.bpsm.edn.parser.CollectionBuilder
import us.bpsm.edn.parser.Parsers
import us.bpsm.edn.parser.TagHandler
import java.net.URI
import java.time.Instant

/**
 * @author Ewan
 */

object Term {
    @Suppress("UNCHECKED_CAST")
    private val functionApplicationBuilder: CollectionBuilder = object : CollectionBuilder {
        var index = 0
        var function: Fn? = null
        var args: Any? = null
        override fun add(o: Any?) {
            when (index) {
                0 -> function = FnIdentifier(o as? Symbol ?: throw SyntaxError("The first element in a function application list must be a symbol"))
                1 -> {
                    args = when (o) {
                        is Map<*,*> -> {
                            if (o.keys.all { it is Keyword }) o
                            else throw SyntaxError("Named argument map keys must be keywords")
                        }
                        is RandomAccess -> o
                        else -> throw SyntaxError("The function application argument term must be either a map or vector, but got '$o'")
                    }
                }
                else -> throw SyntaxError("Function application term may contain only two sub-terms - a function identifier (symbol) term and an (optional) argument (map or vector) term")
            }
            index++
        }
        override fun build(): FnApplication<*> {
            try {
                val fn = function ?: throw AssertionError()
                return when (args) {
                    null -> FnApplicationPositionalArgs(fn)
                    is RandomAccess -> FnApplicationPositionalArgs(fn, args as List<Any?>)
                    is Map<*, *> -> FnApplicationNamedArgs(fn, args as Map<Keyword, Any?>)
                    else -> throw AssertionError()
                }
            } finally {
                reset()
            }
        }
        private fun reset() {
            index = 0
            function = null
            args = null
        }
    }

    private val fnIdentifierTagHandler = TagHandler { tag, value ->
        FnIdentifier(value as? Symbol ?: throw SyntaxError("The value following a function identifier tag ('$tag') must be a symbol"))
    }

    private val instHandler = TagHandler { tag, value ->
        Instant.parse(value as? String ?: throw SyntaxError("The value following an instant tag ('$tag') must be a string literal"))
    }

    private val config = {
        Parsers.newParserConfigBuilder()
            .setListFactory { functionApplicationBuilder }
            .putTagHandler(Tag.newTag("fn"), fnIdentifierTagHandler)
            .putTagHandler(Tag.newTag("inst"), instHandler)
            .putTagHandler(Tag.newTag("uri"), { _, value -> URI(value as String) })
            .build()
    }

    operator fun invoke(edn: String): Any? = Parsers.newParser(config()).nextValue(Parsers.newParseable(edn))
}

class SyntaxError(message: String) : RuntimeException(message)

interface Fn

data class FnIdentifier(val symbol: Symbol): Fn

abstract class FnApplication<out T>(open val fn: Fn, open val args: T)

data class FnApplicationPositionalArgs(override val fn: Fn, override val args: List<Any?> = listOf()): FnApplication<List<Any?>>(fn, args)

data class FnApplicationNamedArgs(override val fn: Fn, override val args: Map<Keyword, Any?>): FnApplication<Map<Keyword, Any?>>(fn, args)