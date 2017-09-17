import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import us.bpsm.edn.parser.Parsers
import java.math.BigDecimal
import java.math.BigInteger

/**
 * @author Ewan
 */
sealed class Term {
    sealed class Value<out T>(open val value: T): Term() {
        sealed class Atom<out T>(override val value: T) : Value<T>(value) {
            object Nil : Atom<Nil>(Nil)
            data class String(override val value: kotlin.String): Atom<kotlin.String>(value)
            data class Int(override val value: BigInteger): Atom<BigInteger>(value)
            data class Decimal(override val value: BigDecimal): Atom<BigDecimal>(value)
            data class Char(override val value: kotlin.Char): Atom<kotlin.Char>(value)
            data class Bool(override val value: Boolean): Atom<Boolean>(value)
            data class Keyword(override val value: us.bpsm.edn.Keyword): Atom<us.bpsm.edn.Keyword>(value)
            data class Symbol(override val value: us.bpsm.edn.Symbol): Atom<us.bpsm.edn.Symbol>(value)
        }
        sealed class Container<out T>(override val value: T) : Value<T>(value) {
            data class List(override val value: kotlin.collections.List<Term>): Container<kotlin.collections.List<Term>>(value)
            data class Set(override val value: kotlin.collections.Set<Term>): Container<kotlin.collections.Set<Term>>(value)
            data class Map(override val value: kotlin.collections.Map<Term,Term>): Container<kotlin.collections.Map<Term,Term>>(value)
            data class KeywordMap(override val value: kotlin.collections.Map<Term.Value.Atom.Keyword, Term>): Container<kotlin.collections.Map<Term.Value.Atom.Keyword, Term>>(value)
        }
    }
    data class FunctionApplication(val symbol: Value.Atom.Symbol, val args: Value.Container.KeywordMap = Value.Container.KeywordMap(emptyMap())): Term()
    companion object {
        fun parse(edn: kotlin.String): Term {
            val value = Parsers.newParser(Parsers.defaultConfiguration()).nextValue(Parsers.newParseable(edn))
            return of(value)
        }
        fun of(value: Any?): Term {
            return if (value == null) Value.Atom.Nil else when (value) {
                is Term -> value
                is String -> Value.Atom.String(value)
                is BigInteger -> Value.Atom.Int(value)
                is Long -> Value.Atom.Int(BigInteger.valueOf(value))
                is Int -> Value.Atom.Int(BigInteger.valueOf(value.toLong()))
                is BigDecimal -> Value.Atom.Decimal(value)
                is Double -> Value.Atom.Decimal(BigDecimal.valueOf(value))
                is Float -> Value.Atom.Decimal(BigDecimal.valueOf(value.toDouble()))
                is Char -> Value.Atom.Char(value)
                is Boolean -> Value.Atom.Bool(value)
                is Symbol -> Value.Atom.Symbol(value)
                is Keyword -> Value.Atom.Keyword(value)
                is RandomAccess -> Value.Container.List((value as kotlin.collections.List<*>).map { of(it) })
                is kotlin.collections.Set<*> -> Value.Container.Set(value.map { of(it) }.toSet())
                is kotlin.collections.Map<*,*> -> Value.Container.Map(value.map { Pair(of(it.key), of(it.value)) }.toMap())
                is kotlin.collections.List<*> -> {
                    if (value.size < 1 || value.size > 2) throw SyntaxError("Bad function application")
                    val identifier = value[0] as? Symbol ?: throw SyntaxError("Bad function application")
                    if (value.size == 1) FunctionApplication(Value.Atom.Symbol(identifier))
                    else {
                        val args = value[1]
                        when (args) {
                            is kotlin.collections.Map<*,*> -> FunctionApplication(Value.Atom.Symbol(identifier), Value.Container.KeywordMap(args.map { Pair(Term.Value.Atom.Keyword(it.key as Keyword), of(it.value)) }.toMap()))
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