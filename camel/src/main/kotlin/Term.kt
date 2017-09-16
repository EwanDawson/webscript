import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import us.bpsm.edn.parser.Parsers
import java.math.BigDecimal
import java.math.BigInteger

/**
 * @author Ewan
 */
sealed class Term {
    interface Value<out T> {
        val value: T
    }
    interface Compound {
        val terms: kotlin.collections.List<Term>
    }
    sealed class Atom<out T>(override val value: T) : Term(), Value<T> {
        object Nil : Atom<Nil>(Nil)
        data class String(override val value: kotlin.String): Atom<kotlin.String>(value)
        data class Int(override val value: BigInteger): Atom<BigInteger>(value)
        data class Decimal(override val value: BigDecimal): Atom<BigDecimal>(value)
        data class Char(override val value: kotlin.Char): Atom<kotlin.Char>(value)
        data class Bool(override val value: Boolean): Atom<Boolean>(value)
        data class Keyword(override val value: us.bpsm.edn.Keyword): Atom<us.bpsm.edn.Keyword>(value)
        data class Symbol(override val value: us.bpsm.edn.Symbol): Atom<us.bpsm.edn.Symbol>(value)
    }
    sealed class Container<out T>(override val value: T) : Term(), Value<T>, Compound {
        data class List(override val value: kotlin.collections.List<Term>): Container<kotlin.collections.List<Term>>(value) {
            override val terms: kotlin.collections.List<Term>
                get() = value
        }
        data class Set(override val value: kotlin.collections.Set<Term>): Container<kotlin.collections.Set<Term>>(value) {
            override val terms: kotlin.collections.List<Term>
                get() = value.toList()
        }
        data class Map(override val value: kotlin.collections.Map<Term,Term>): Container<kotlin.collections.Map<Term,Term>>(value) {
            override val terms: kotlin.collections.List<Term>
                get() = value.entries.map { List(listOf(it.key, it.value)) }
        }
        data class KeywordMap(override val value: kotlin.collections.Map<Term.Atom.Keyword, Term>): Container<kotlin.collections.Map<Term.Atom.Keyword, Term>>(value) {
            override val terms: kotlin.collections.List<Term>
                get() = value.entries.map { List(listOf(it.key, it.value)) }
        }
    }
    data class FunctionApplication(val symbol: Atom.Symbol, val args: Container.KeywordMap = Container.KeywordMap(emptyMap())): Term(), Compound {
        override val terms: kotlin.collections.List<Term>
            get() = listOf(symbol, args)
    }
    companion object {
        fun parse(edn: kotlin.String): Term {
            val value = Parsers.newParser(Parsers.defaultConfiguration()).nextValue(Parsers.newParseable(edn))
            return of(value)
        }
        fun of(value: Any?): Term {
            return if (value == null) Atom.Nil else when (value) {
                is Term -> value
                is String -> Atom.String(value)
                is BigInteger -> Atom.Int(value)
                is Long -> Atom.Int(BigInteger.valueOf(value))
                is Int -> Atom.Int(BigInteger.valueOf(value.toLong()))
                is BigDecimal -> Atom.Decimal(value)
                is Double -> Atom.Decimal(BigDecimal.valueOf(value))
                is Float -> Atom.Decimal(BigDecimal.valueOf(value.toDouble()))
                is Char -> Atom.Char(value)
                is Boolean -> Atom.Bool(value)
                is Symbol -> Atom.Symbol(value)
                is Keyword -> Atom.Keyword(value)
                is RandomAccess -> Container.List((value as kotlin.collections.List<*>).map { of(it) })
                is kotlin.collections.Set<*> -> Container.Set(value.map { of(it) }.toSet())
                is kotlin.collections.Map<*,*> -> Container.Map(value.map { Pair(of(it.key), of(it.value)) }.toMap())
                is kotlin.collections.List<*> -> {
                    if (value.size < 1 || value.size > 2) throw SyntaxError("Bad function application")
                    val identifier = value[0] as? Symbol ?: throw SyntaxError("Bad function application")
                    if (value.size == 1) FunctionApplication(Atom.Symbol(identifier))
                    else {
                        val args = value[1]
                        when (args) {
                            is kotlin.collections.Map<*,*> -> FunctionApplication(Atom.Symbol(identifier), Container.KeywordMap(args.map { Pair(Term.Atom.Keyword(it.key as Keyword), of(it.value)) }.toMap()))
                            else -> throw SyntaxError("Bad function application")
                        }
                    }
                }
                else -> throw SyntaxError("Cannot create Term from ${value::class} '$value'")
            }
        }
    }
}

class SyntaxError(message: String) : RuntimeException(message)