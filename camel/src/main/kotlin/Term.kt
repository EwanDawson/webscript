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
    private val functionApplicationBuilder: CollectionBuilder = object : CollectionBuilder {
        var index = 0
        var function: Fn? = null
        val argNames = mutableListOf<Keyword>()
        val argValues = mutableListOf<Any?>()
        override fun add(o: Any?) {
            when {
                index == 0 -> function = o as? Fn ?: throw SyntaxError("The first element in a function application list must be a Fn")
                isOdd(index) -> argNames.add(o as? Keyword ?: throw SyntaxError("Arguments must come in keyword-term pairs"))
                isEven(index) -> argValues.add(o)
            }
            index++
        }
        override fun build(): FnApplication {
            val fn = function ?: throw AssertionError()
            return FnApplication(fn, argNames.zip(argValues).toMap())
        }
        private fun isOdd(x: Int) = x % 2 == 1
        private fun isEven(x: Int) = !isOdd(x)
    }

    private val fnIdentifierTagHandler = TagHandler { tag, value ->
        FnIdentifier(value as? Symbol ?: throw SyntaxError("The value following a function identifier tag ('$tag') must be a symbol"))
    }

    private val fnScriptTagHandler = TagHandler { tag, value ->
        when (value) {
            is String -> FnInlineScript(tag.name, value)
            is URI -> FnURIScript(tag.name, value)
            else -> throw SyntaxError("The value following a function script tag ('$tag') must be a script literal or URI")
        }
    }

    private val instHandler = TagHandler { tag, value ->
        Instant.parse(value as? String ?: throw SyntaxError("The value following an instant tag ('$tag') must be a string literal"))
    }

    private val config = Parsers.newParserConfigBuilder()
        .setListFactory { functionApplicationBuilder }
        .putTagHandler(Tag.newTag("fn"), fnIdentifierTagHandler)
        .putTagHandler(Tag.newTag("lang", "groovy"), fnScriptTagHandler)
        .putTagHandler(Tag.newTag("inst"), instHandler)
        .putTagHandler(Tag.newTag("uri"), { _, value -> URI(value as String) })
        .build()

    private val parser = Parsers.newParser(config)

    operator fun invoke(edn: String): Any? = parser.nextValue(Parsers.newParseable(edn))
}

class SyntaxError(message: String) : RuntimeException(message)

interface Fn

data class FnIdentifier(val symbol: Symbol): Fn

abstract class FnScript(open val lang: String): Fn
data class FnInlineScript(override val lang: String, val source: CharSequence): FnScript(lang)
data class FnURIScript(override val lang: String, val sourceUri: URI): FnScript(lang)

data class FnApplication(val fn: Fn, val args: Map<Keyword, Any?>)