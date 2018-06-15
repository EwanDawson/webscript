package io.kutoa

import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import us.bpsm.edn.parser.Parsers
import us.bpsm.edn.printer.Printers
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
sealed class Term {

    sealed class Atom<out T:Any>(open val value: T) : Term() {

        sealed class Constant<out T:Any>(override val value: T) : Atom<T>(value) {

            object Nil : Constant<Nil>(Nil)

            data class String(override val value: kotlin.String): Constant<kotlin.String>(value) {
                override fun toString() = toEDNPretty()
            }

            data class Integer(override val value: BigInteger): Constant<BigInteger>(value) {
                constructor(value: Number) : this(value as? BigInteger ?: BigInteger.valueOf(
                    value.toLong()))
                override fun toString() = toEDNPretty()
            }

            data class Decimal(override val value: BigDecimal): Constant<BigDecimal>(value) {
                constructor(value: Number) : this(value as? BigDecimal ?: BigDecimal.valueOf(
                    value.toDouble()))
                override fun toString() = toEDNPretty()
            }

            data class Character(override val value: kotlin.Char): Constant<kotlin.Char>(value) {
                override fun toString() = toEDNPretty()
            }

            data class Boolean(override val value: kotlin.Boolean): Constant<kotlin.Boolean>(value) {
                override fun toString() = toEDNPretty()
            }

            data class Keyword(override val value: us.bpsm.edn.Keyword): Constant<us.bpsm.edn.Keyword>(value) {
                constructor(prefix: kotlin.String, name: kotlin.String) : this (
                    us.bpsm.edn.Keyword.newKeyword(prefix, name))
                constructor(name: kotlin.String) : this (us.bpsm.edn.Keyword.newKeyword(name))
                override fun toString() = toEDNPretty()
            }

            data class Error(override val value: ErrorInfo) : Atom<ErrorInfo>(value) {
                constructor(throwable: Throwable) : this(ErrorInfo(throwable))
                constructor(name: kotlin.String, message: kotlin.String) : this(ErrorInfo(name, message))
                override val isConstant = true
                // TODO: Create EDN representation of Error
                override fun toString() = value.toString()
            }

            // TODO("Add Lambda atom - should be constant, allowing from closure over variables from currently scoped binding")
            override val isConstant = true
        }

        data class Symbol(override val value: us.bpsm.edn.Symbol): Atom<us.bpsm.edn.Symbol>(value) {
            constructor(prefix: String, name: String) : this(us.bpsm.edn.Symbol.newSymbol(prefix, name))
            constructor(fqn: String) : this(
                if (fqn.contains('/'))
                    us.bpsm.edn.Symbol.newSymbol(fqn.substringBefore('/'), fqn.substringAfter('/'))
                else
                    us.bpsm.edn.Symbol.newSymbol(fqn)
            )
            override val isConstant = false
            override fun toString() = toEDNPretty()
        }

        override fun unwrap() = value
    }

    sealed class Compound<out T:Any>(open val value: T) : Term() {

        data class List(override val value: kotlin.collections.List<Term>): Compound<kotlin.collections.List<Term>>(value) {
            override val size = value.size
            override fun map(mapping: (Term) -> Term): List = value.map(mapping).toTerm()
            override val isConstant get() = value.all(Term::isConstant)
            override fun unwrap() = value.map(Term::unwrap)
            override fun toString() = toEDNPretty()
        }

        data class Set(override val value: kotlin.collections.Set<Term>): Compound<kotlin.collections.Set<Term>>(value) {
            override val size = value.size
            override fun map(mapping: (Term) -> Term): Set = value.map(mapping).toSet().toTerm()
            override val isConstant get() = value.all(Term::isConstant)
            override fun unwrap() = value.map(Term::unwrap).toSet()
            override fun toString() = toEDNPretty()
        }

        data class Map(override val value: kotlin.collections.Map<out TConstant<*>, Term>): Compound<kotlin.collections.Map<out TConstant<*>, Term>>(value) {
            override val size = value.size
            override fun map(mapping: (Term) -> Term): Map = value.mapValues { e -> mapping(e.value) }.toTerm()
            override val isConstant get() = value.values.all(Term::isConstant)
            override fun unwrap() = value.map { Pair(it.key.unwrap(), it.value.unwrap()) }.toMap()
            override fun toString() = toEDNPretty()
        }

        abstract val size : Int

        abstract fun map(mapping: (Term) -> Term) : Compound<T>
    }

    data class Application(val symbol: TSymbol, val args: kotlin.collections.List<Term> = emptyList()): Term() {
        override val isConstant get() = false
        override fun unwrap() = LinkedList<Any?>(listOf(symbol.unwrap()) + args.map { it.unwrap() })
        override fun toString() = toEDNPretty()
    }

    abstract fun unwrap(): Any

    // TODO: Custom EDN pretty printing. For now, default is better than the pretty printer provided
    protected fun toEDNPretty() = toEDN()

    override fun toString() = toEDNPretty()

    fun toEDN() = Printers.printString(Printers.defaultPrinterProtocol(),
                                       unwrap())!!
    abstract val isConstant: Boolean

    companion object {
        fun parse(edn: kotlin.String): Term {
            val value = Parsers.newParser(Parsers.defaultConfiguration()).nextValue(
                Parsers.newParseable(edn))
            return of(value)
        }

        fun of(value: Any?): Term {
            return if (value == null) TNil else when (value) {
                is Term -> value
                is String -> TString(value)
                is BigInteger -> TInteger(value)
                is Long -> TInteger(BigInteger.valueOf(value))
                is Int -> TInteger(BigInteger.valueOf(value.toLong()))
                is BigDecimal -> TDecimal(value)
                is Double -> TDecimal(BigDecimal.valueOf(value))
                is Float -> TDecimal(BigDecimal.valueOf(value.toDouble()))
                is Char -> TCharacter(value)
                is Boolean -> TBoolean(value)
                is Symbol -> TSymbol(value)
                is Keyword -> TKeyword(value)
                is RandomAccess -> throw SyntaxError("Not a valid expression: '$value'")
                is kotlin.collections.List<*> -> {
                    val list = value as kotlin.collections.List<Any?>
                    val firstTerm = list.firstOrNull()?.toTerm()
                    if (firstTerm != null && firstTerm is TSymbol) TApplication(firstTerm,
                                                                                                  list.drop(1).map(
                                                                                                      Companion::of))
                    else list.toTerm()
                }
                is Set<*> -> value.toTerm()
                is Map<*, *> -> value.toTerm()
                else -> throw SyntaxError("Cannot create Term from ${value::class} '$value'")
            }
        }

        fun kmap(value: Map<String, Any?>) = TMap(
            value.mapKeys { TKeyword(it.key) }.mapValues { of(it.value) })
    }
}