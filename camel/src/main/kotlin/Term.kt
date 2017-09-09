import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import us.bpsm.edn.Tag
import us.bpsm.edn.parser.CollectionBuilder
import us.bpsm.edn.parser.Parsers
import us.bpsm.edn.parser.TagHandler
import us.bpsm.edn.printer.Printers
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URL
import java.time.Instant

/**
 * @author Ewan
 */

open class Term(open val term: String) {
    companion object {
        private val arrayBuilder: CollectionBuilder = object : CollectionBuilder {
            val list = mutableListOf<Term>()
            override fun add(o: Any?) {
                list.add(Term.of(Printers.printString(Printers.defaultPrinterProtocol(), o)))
            }
            override fun build(): ArrayTerm {
                return ArrayTerm("", list.toTypedArray())
            }
        }

        private val setBuilder: CollectionBuilder = object : CollectionBuilder {
            val set = mutableSetOf<Term>()
            override fun add(o: Any?) {
                set.add(Term.of(Printers.printString(Printers.defaultPrinterProtocol(), o)))
            }
            override fun build(): SetTerm {
                return SetTerm("", set.toSet())
            }
        }

        private val mapBuilder: CollectionBuilder = object : CollectionBuilder {
            val list = mutableListOf<Term>()
            override fun add(o: Any?) {
                list.add(Term.of(Printers.printString(Printers.defaultPrinterProtocol(), o)))
            }
            override fun build(): MapTerm {
                return MapTerm("", list.zip(list.subList(1, list.size - 1)).toMap())
            }
        }

        private val functionApplicationBuilder: CollectionBuilder = object : CollectionBuilder {
            var function: Fn? = null
            val argNames = mutableListOf<Keyword>()
            val argValues = mutableListOf<Term>()
            override fun add(o: Any?) {
                if (function == null) {
                    if (o !is Fn) throw IllegalStateException("First element in a list must be a function")
                    function = o
                } else {
                    if (o is Keyword) {
                        if (argNames.size != argValues.size) throw IllegalStateException("Arguments must come in keyword-term pairs")
                        argNames.add(o)
                    } else {
                        if (argNames.size != argValues.size + 1) throw IllegalStateException("Arguments must come in keyword-term pairs")
                        argValues.add(Term.of(Printers.printString(Printers.defaultPrinterProtocol(), o)))
                    }
                }
            }
            override fun build(): FnApplication {
                val fn = function ?: throw AssertionError()
                return FnApplication("", fn, argNames.zip(argValues).toMap())
            }
        }

        private val fnIdentifierTagHandler = TagHandler { tag, value ->
            val tagEdn = Printers.printString(Printers.defaultPrinterProtocol(), tag)
            val valueEdn = Printers.printString(Printers.defaultPrinterProtocol(), value)
            FnIdentifier("$tagEdn $valueEdn", value as Symbol)
        }

        private val fnScriptTagHandler = TagHandler { tag, value ->
            val tagEdn = Printers.printString(Printers.defaultPrinterProtocol(), tag)
            val valueEdn = Printers.printString(Printers.defaultPrinterProtocol(), value)
            FnScript("$tagEdn $valueEdn", tag.name, Term.of(valueEdn))
        }

        private val instHandler = TagHandler { _, value ->
            Instant.parse(value as String)
        }

        private val config = Parsers.newParserConfigBuilder()
            .setVectorFactory { arrayBuilder }
            .setSetFactory { setBuilder }
            .setMapFactory { mapBuilder }
            .setListFactory { functionApplicationBuilder }
            .putTagHandler(Tag.newTag("fn"), fnIdentifierTagHandler)
            .putTagHandler(Tag.newTag("lang", "groovy"), fnScriptTagHandler)
            .putTagHandler(Tag.newTag("inst"), instHandler)
            .build()

        private val parser = Parsers.newParser(config)

        fun of(edn: String): Term {
            val values = Parsers.newParseable(edn)
            val value = parser.nextValue(values)
            if (value is String && (value.startsWith("http://") || value.startsWith("https://")))
                return URLTerm(edn, URL(value))
            return when (value) {
                null -> Nil
                true -> True
                false -> False
                is Long -> IntTerm(edn, BigInteger.valueOf(value))
                is BigInteger -> IntTerm(edn, value)
                is Double -> Real(edn, BigDecimal.valueOf(value))
                is BigDecimal -> Real(edn, value)
                is Char -> CharTerm(edn, value)
                is String -> StringTerm(edn, value)
                is Instant -> DateTime(edn, value)
                is ArrayTerm -> ArrayTerm(edn, value.value)
                is SetTerm -> SetTerm(edn, value.value)
                is MapTerm -> MapTerm(edn, value.value)
                is FnApplication -> FnApplication(edn, value.function, value.args)
                else -> throw IllegalArgumentException("Failed to parse term: $edn")
            }
        }
    }
    override fun toString() = "Term($term)"
    override fun equals(other: Any?) = other is Term && term == other.term
    override fun hashCode() = 31 * super.hashCode() + term.hashCode()
}

abstract class Value<out T>(override val term: String, open val value: T): Term(term) {
    override fun equals(other: Any?) = other is Value<*> && value == other.value
    override fun hashCode() = 31 * super.hashCode() + (value?.hashCode() ?: 0)
}

object Nil: Value<Any?>("nil", null)

abstract class Bool(override val term: String, override val value: Boolean): Value<Boolean>(term, value)

object True: Bool("true", true)

object False: Bool("false", false)

class StringTerm(override val term: String, override val value: String): Value<String>(term, value)

class IntTerm(override val term: String, override val value: BigInteger): Value<BigInteger>(term, value)

class CharTerm(override val term: String, override val value: Char): Value<Char>(term, value)

class Real(override val term: String, override val value: BigDecimal): Value<BigDecimal>(term, value)

class ArrayTerm(override val term: String, override val value: Array<Term>): Value<Array<Term>>(term, value)

class DateTime(override val term: String, override val value: Instant): Value<Instant>(term, value)

class SetTerm(override val term: String, override val value: Set<Term>): Value<Set<Term>>(term, value)

class MapTerm(override val term: String, override val value: Map<Term, Term>): Value<Map<Term, Term>>(term, value)

class URLTerm(override val term: String, val url: URL): Term(term)

abstract class Fn(override val term: String): Term(term)

class FnIdentifier(override val term: String, val symbol: Symbol): Fn(term)

class FnScript(override val term: String, val lang: String, val source: Term): Fn(term)

class FnApplication(override val term: String, val function: Fn, val args: Map<Keyword, Term>): Term(term)