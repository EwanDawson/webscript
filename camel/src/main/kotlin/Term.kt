import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import us.bpsm.edn.parser.Parsers
import us.bpsm.edn.printer.Printers
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

/**
 * @author Ewan
 */
sealed class Term {
    sealed class Value<out T:Any>(open val value: T): Term() {
        sealed class Atom<out T:Any>(override val value: T) : Value<T>(value) {
            object Nil : Atom<Nil>(Nil)
            data class String(override val value: kotlin.String): Atom<kotlin.String>(value)
            data class Int(override val value: BigInteger): Atom<BigInteger>(value)
            data class Decimal(override val value: BigDecimal): Atom<BigDecimal>(value)
            data class Char(override val value: kotlin.Char): Atom<kotlin.Char>(value)
            data class Bool(override val value: Boolean): Atom<Boolean>(value)
            data class Keyword(override val value: us.bpsm.edn.Keyword): Atom<us.bpsm.edn.Keyword>(value)
            data class Symbol(override val value: us.bpsm.edn.Symbol): Atom<us.bpsm.edn.Symbol>(value)
            override fun unwrap() = value
        }
        sealed class Container<out T:Any>(override val value: T) : Value<T>(value) {
            data class List(override val value: kotlin.collections.List<Term>): Container<kotlin.collections.List<Term>>(value) {
                override fun unwrap() = value.map { it.unwrap() }
            }
            data class Set(override val value: kotlin.collections.Set<Term>): Container<kotlin.collections.Set<Term>>(value) {
                override fun unwrap() = value.map { it.unwrap() }.toSet()
            }
            data class Map(override val value: kotlin.collections.Map<Term,Term>): Container<kotlin.collections.Map<Term,Term>>(value) {
                override fun unwrap() = value.map { Pair(it.key.unwrap(), it.value.unwrap()) }.toMap()
            }
            data class KeywordMap(override val value: kotlin.collections.Map<Term.Value.Atom.Keyword, Term>): Container<kotlin.collections.Map<Term.Value.Atom.Keyword, Term>>(value) {
                override fun unwrap() = value.map { Pair(it.key.unwrap(), it.value.unwrap()) }.toMap()
            }
        }
    }
    data class FunctionApplication(val symbol: Value.Atom.Symbol, val args: List<Term> = emptyList()): Term() {
        override fun unwrap() = LinkedList<Any?>(listOf(symbol.unwrap()) +  args.map { it.unwrap() })
    }

    internal abstract fun unwrap(): Any

    fun toEDN() = Printers.printString(Printers.defaultPrinterProtocol(), unwrap())!!

    companion object {
        fun parse(edn: kotlin.String): Term {
            val value = Parsers.newParser(Parsers.defaultConfiguration()).nextValue(Parsers.newParseable(edn))
            return of(value)
        }
        fun of(value: Any?): Term {
            return if (value == null) nil else when (value) {
                is Term -> value
                is String -> string(value)
                is BigInteger -> int(value)
                is Long -> int(value)
                is Int -> int(value)
                is BigDecimal -> decimal(value)
                is Double -> decimal(value)
                is Float -> decimal(value)
                is Char -> char(value)
                is Boolean -> bool(value)
                is Symbol -> symbol(value)
                is Keyword -> keyword(value)
                is RandomAccess -> list(value as List<Any?>)
                is Set<*> -> set(value)
                is Map<*,*> -> map(value)
                is List<*> -> {
                    if (value.isEmpty()) throw SyntaxError("Bad function application")
                    val identifier = value[0] as? Symbol ?: throw SyntaxError("Bad function application")
                    function(identifier, value.drop(1).map { of(it) })
                }
                else -> throw SyntaxError("Cannot create Term from ${value::class} '$value'")
            }
        }
        fun function(symbol: Symbol, args: List<Any?>) = Term.FunctionApplication(symbol(symbol), args.map { of(it) })
        fun function(symbol: Value.Atom.Symbol, args: List<Term>) = Term.FunctionApplication(symbol, args)
        fun symbol(prefix: String, name: String) = Term.Value.Atom.Symbol(Symbol.newSymbol(prefix, name))
        fun symbol(name: String) = Term.Value.Atom.Symbol(Symbol.newSymbol(name))
        fun symbol(symbol: Symbol) = Term.Value.Atom.Symbol(symbol)
        fun string(value: String) = Term.Value.Atom.String(value)
        fun int(value: Int) = Term.Value.Atom.Int(BigInteger.valueOf(value.toLong()))
        fun int(value: Long) = Term.Value.Atom.Int(BigInteger.valueOf(value))
        fun int(value: BigInteger) = Term.Value.Atom.Int(value)
        fun decimal(value: Float) = Term.Value.Atom.Decimal(BigDecimal.valueOf(value.toDouble()))
        fun decimal(value: Double) = Term.Value.Atom.Decimal(BigDecimal.valueOf(value))
        fun decimal(value: BigDecimal) = Term.Value.Atom.Decimal(value)
        fun char(value: Char) = Term.Value.Atom.Char(value)
        fun keyword(keyword: Keyword) = Term.Value.Atom.Keyword(keyword)
        fun keyword(prefix: String, name: String) = Term.Value.Atom.Keyword(Keyword.newKeyword(prefix, name))
        fun keyword(name: String) = Term.Value.Atom.Keyword(Keyword.newKeyword(name))
        fun list(value: List<Any?>) = Term.Value.Container.List(value.map { of(it) })
        fun map(vararg pairs: Pair<Term.Value.Atom.Keyword, Term>) = Term.Value.Container.KeywordMap(mapOf(*pairs))
        fun map(vararg pairs: Pair<Term, Term>) = Term.Value.Container.Map(mapOf(*pairs))
        fun map(value: Map<*, *>) = Term.Value.Container.Map(value.map { Pair(of(it.key), of(it.value)) }.toMap())
        fun set(value: Set<Any?>) = Term.Value.Container.Set(value.map { of(it) }.toSet())
        fun bool(value: Boolean) = Term.Value.Atom.Bool(value)
        val nil = Term.Value.Atom.Nil
    }
}

class SyntaxError(message: String) : RuntimeException(message)