package io.kutoa

import groovy.lang.Binding
import groovy.lang.Closure
import groovy.lang.GroovyShell
import org.apache.camel.FluentProducerTemplate
import org.apache.camel.impl.DefaultCamelContext
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
            data class String(override val value: kotlin.String): Constant<kotlin.String>(value)
            data class Integer(override val value: BigInteger): Constant<BigInteger>(value) {
                constructor(value: Number) : this(value as? BigInteger ?: BigInteger.valueOf(value.toLong()))
            }
            data class Decimal(override val value: BigDecimal): Constant<BigDecimal>(value) {
                constructor(value: Number) : this(value as? BigDecimal ?: BigDecimal.valueOf(value.toDouble()))
            }
            data class Character(override val value: kotlin.Char): Constant<kotlin.Char>(value)
            data class Boolean(override val value: kotlin.Boolean): Constant<kotlin.Boolean>(value)
            data class Keyword(override val value: us.bpsm.edn.Keyword): Constant<us.bpsm.edn.Keyword>(value) {
                constructor(prefix: kotlin.String, name: kotlin.String) : this (us.bpsm.edn.Keyword.newKeyword(prefix, name))
            }
            // TODO("Add Lambda atom - should be constant, allowing from closure over variables from currently scoped binding")
            override val isConstant = true
        }
        data class Error(override val value: ErrorInfo) : Atom<ErrorInfo>(value) {
            constructor(throwable: Throwable) : this(ErrorInfo(throwable))
            constructor(name: String, message: String) : this(ErrorInfo(name, message))
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
        }
        override fun unwrap() = value
    }
    sealed class Compound<out T:Any>(open val value: T) : Term() {
        data class List(override val value: kotlin.collections.List<Term>): Compound<kotlin.collections.List<Term>>(value) {
            override val size = value.size
            override fun map(mapping: (Term) -> Term): List = Term.list(value.map(mapping))
            override val isConstant get() = value.all(Term::isConstant)
            override fun unwrap() = value.map(Term::unwrap)
        }
        data class Set(override val value: kotlin.collections.Set<Term>): Compound<kotlin.collections.Set<Term>>(value) {
            override val size = value.size
            override fun map(mapping: (Term) -> Term): Set = Term.set(value.map(mapping).toSet())
            override val isConstant get() = value.all(Term::isConstant)
            override fun unwrap() = value.map(Term::unwrap).toSet()
        }
        data class Map(override val value: kotlin.collections.Map<TConstant<*>,Term>): Compound<kotlin.collections.Map<TConstant<*>,Term>>(value) {
            override val size = value.size
            override fun map(mapping: (Term) -> Term): Map = Term.map(value.mapValues { e -> mapping(e.value) })
            override val isConstant get() = value.values.all(Term::isConstant)
            override fun unwrap() = value.map { Pair(it.key.unwrap(), it.value.unwrap()) }.toMap()
        }
        data class KeywordMap(override val value: kotlin.collections.Map<TKeyword, Term>): Compound<kotlin.collections.Map<TKeyword, Term>>(value) {
            override val size = value.size
            override fun map(mapping: (Term) -> Term): KeywordMap = TKeywordMap(value.mapValues { e -> mapping(e.value) })
            override val isConstant get() = value.values.all(Term::isConstant)
            override fun unwrap() = value.map { Pair(it.key.unwrap(), it.value.unwrap()) }.toMap()
            companion object {
                val EMPTY = KeywordMap(mapOf())
            }
        }
        abstract val size : Int
        abstract fun map(mapping: (Term) -> Term) : Compound<T>
    }
    data class Application(val symbol: TSymbol, val args: kotlin.collections.List<Term> = emptyList()): Term() {
        override val isConstant get() = false
        override fun unwrap() = LinkedList<Any?>(listOf(symbol.unwrap()) + args.map { it.unwrap() })
    }

    internal abstract fun unwrap(): Any

    fun toEDN() = Printers.printString(Printers.defaultPrinterProtocol(), unwrap())!!
    abstract val isConstant: Boolean

    companion object {
        fun parse(edn: kotlin.String): Term {
            val value = Parsers.newParser(Parsers.defaultConfiguration()).nextValue(Parsers.newParseable(edn))
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
                is kotlin.collections.List<*> -> list(value as kotlin.collections.List<Any?>)
                is Set<*> -> set(value)
                is Map<*, *> -> map(value)
                else -> throw SyntaxError("Cannot create Term from ${value::class} '$value'")
            }
        }

        fun list(value: kotlin.collections.List<Any?>) = TList(value.map { of(it) })
        fun map(value: Map<*, *>) = TMap(value.map { Pair(of(it.key) as? TConstant<*> ?: throw SyntaxError("Map key must be a Constant"), of(it.value)) }.toMap())
        fun set(value: Set<Any?>) = TSet(value.map { of(it) }.toSet())
    }
}

data class ErrorInfo(val code: String, val message: String) {
    constructor(throwable: Throwable) : this(throwable::class.java.name, throwable.message?:"")
}

typealias TBoolean = Term.Atom.Constant.Boolean
typealias TCharacter = Term.Atom.Constant.Character
typealias TDecimal = Term.Atom.Constant.Decimal
typealias TInteger = Term.Atom.Constant.Integer
typealias TKeyword = Term.Atom.Constant.Keyword
typealias TNil = Term.Atom.Constant.Nil
typealias TString = Term.Atom.Constant.String
typealias TError = Term.Atom.Error
typealias TConstant<T> = Term.Atom.Constant<T>
typealias TSymbol = Term.Atom.Symbol
typealias TAtom<T> = Term.Atom<T>
typealias TCompound<T> = Term.Compound<T>
typealias TKeywordMap = Term.Compound.KeywordMap
typealias TList = Term.Compound.List
typealias TMap = Term.Compound.Map
typealias TSet = Term.Compound.Set
typealias TApplication = Term.Application

typealias Bindings = Map<TSymbol, Term>

class Computer(builtIns: kotlin.collections.List<Function>, private val cache: Cache) {

    private val macroIdentifier = TSymbol("sys.macro", "identifier")
    private val macroArgs = TSymbol("sys.macro", "args")

    private val builtInsMap = builtIns.fold(mapOf()) { map: Map<TSymbol, Function>, evaluator ->
        map + (evaluator.identifier to evaluator)
    }

    fun evaluate(term: Term, bindings: Bindings) : Evaluation =
        when {
            term.isConstant -> Evaluation.constant(term, bindings)
            else -> when (term) {
                is TConstant<*> -> Evaluation.constant(term, bindings)
                is TError -> Evaluation.constant(term, bindings)
                is TCompound<*> -> evaluateCompound(term, bindings)
                is TSymbol -> evaluateSymbol(term, bindings)
                is TApplication -> evaluateApplication(term, bindings)
            }
        }

    private fun evaluateCompound(term: TCompound<*>, bindings: Bindings) : Evaluation {
        val subSteps = mutableListOf<Evaluation>()
        return try {
            when (term) {
                is TList -> {
                    term.value.forEach { subSteps.add(evaluate(it, bindings)) }
                    Evaluation.compound(term, bindings, Term.list(subSteps.map(Evaluation::result)), subSteps)
                }
                is TSet -> {
                    term.value.forEach { subSteps.add(evaluate(it, bindings)) }
                    Evaluation.compound(term, bindings, Term.set(subSteps.map(Evaluation::result).toSet()), subSteps)
                }
                is TMap, is TKeywordMap -> {
                    val result = term.map {
                        val evaluation = evaluate(it, bindings)
                        subSteps.add(evaluation)
                        evaluation.result
                    }
                    Evaluation.compound(term, bindings, result, subSteps)
                }
            }
        } catch (error: EvaluationError) {
            throw EvaluationError(Evaluation.compound(term, bindings, error.evaluation.result, subSteps.plusElement(error.evaluation).toList()), error)
        }
    }

    private fun evaluateSymbol(term: TSymbol, bindings: Bindings) : Evaluation =
        if (bindings.containsKey(term)) {
            val substep = try {
                evaluate(bindings[term]!!, bindings)
            } catch (error: EvaluationError) {
                error.evaluation
            }
            Evaluation.bindSymbol(term, bindings, substep.result, substep)
        }
        else {
            val error = UnknownSymbolException(term)
            throw EvaluationError(Evaluation.bindSymbol(term, bindings, error), error)
        }

    private fun evaluateApplication(term: TApplication, bindings: Bindings) : Evaluation =
        cache.getOrCompute(term, bindings) {
            when {
                bindings.containsKey(term.symbol) ->
                    evaluateSymbol(term.symbol, createMacroBindings(term.symbol, term.args, bindings))
                builtInsMap.containsKey(term.symbol) ->
                    evaluateBuiltIn(term, builtInsMap[term.symbol]!!, bindings)
                else -> {
                    val error = UnknownSymbolException(term.symbol)
                    throw EvaluationError(Evaluation.bindSymbol(term.symbol, bindings, error), error)
                }
            }
        }

    private fun createMacroBindings(identifier: TSymbol, args: kotlin.collections.List<Term>, bindings: Bindings) : Bindings =
        bindings + mapOf(macroIdentifier to identifier, macroArgs to Term.list(args))

    private fun evaluateBuiltIn(term: TApplication, function: Function, bindings: Bindings) : Evaluation {
        val substeps = term.args.map { evaluate(it, bindings) }
        val evaluation = function.apply(TApplication(term.symbol, substeps.map { it.result }), bindings, this)
        return evaluation.copy(subSteps = substeps + evaluation.subSteps)
    }
}

class EvaluationError(val evaluation: Evaluation, error: Throwable) : RuntimeException(error)

class Cache {

    private val cache = mutableMapOf<Term, MutableMap<Bindings, Term>>()

    fun getOrCompute(term: Term, bindings: Bindings, evaluator: () -> Evaluation): Evaluation {
        val cachedResult = cache[term]?.toList()?.firstOrNull {
            it.first.all { bindings.containsKey(it.key) && bindings[it.key] == it.value }
        }?.second
        return when (cachedResult) {
            null -> evaluator.invoke().let { evaluation ->
                if (!cache.containsKey(term)) cache[term] = mutableMapOf()
                cache[term]!![evaluation.dependencies] = evaluation.result
                evaluation
            }
            else -> Evaluation.cacheHit(term, bindings, cachedResult)
        }
    }

    fun clear() { cache.clear() }
}

data class Evaluation(val input: Term, val bindings: Bindings, val result: Term, val operation: Operation,
                      val subSteps: kotlin.collections.List<Evaluation>, val dependencies: Bindings) {

    companion object {
        fun constant(input: Term, bindings: Bindings) : Evaluation {
            assert(input.isConstant)
            return Evaluation(input, bindings, input, Operation.CONSTANT, emptyList(), emptyMap())
        }

        fun bindSymbol(input: TSymbol, bindings: Bindings, result: Term, substep: Evaluation) : Evaluation {
            return Evaluation(input, bindings, result, Operation.BIND_SYMBOL, listOf(substep), mapOf(input to bindings[input]!!))
        }

        fun bindSymbol(input: TSymbol, bindings: Bindings, throwable: Throwable) : Evaluation {
            return Evaluation(input, bindings, Term.Atom.Error(throwable), Operation.BIND_SYMBOL, emptyList(), emptyMap())
        }

        fun compound(term: TCompound<*>, bindings: Bindings, result: Term,
                     subSteps: kotlin.collections.List<Evaluation>): Evaluation {
            return Evaluation(term, bindings, result, Operation.COMPOUND, subSteps, emptyMap())
        }

        fun cacheHit(term: Term, bindings: Bindings, cachedResult: Term): Evaluation {
            return Evaluation(term, bindings, cachedResult, Operation.CACHE_HIT, emptyList(), emptyMap())
        }

        fun applyFunction(term: TApplication, bindings: Bindings, result: Term) =
            Evaluation(term, bindings, result, Operation.APPLY_FUNCTION, emptyList(), emptyMap())
    }

    enum class Operation {
        CONSTANT,
        COMPOUND,
        BIND_SYMBOL,
        APPLY_FUNCTION,
        CACHE_HIT
    }
}

abstract class Function(val identifier: TSymbol) {
    // TODO("Allow functions to return lambdas")
    abstract fun apply(term: TApplication, bindings: Bindings, computer: Computer) : Evaluation
}

class List : Function(TSymbol("sys", "list")) {
    override fun apply(term: TApplication, bindings: Bindings, computer: Computer)
        = Evaluation.applyFunction(term, bindings, TList(term.args))

}
class Get : Function(TSymbol("sys", "get")) {
    override fun apply(term: TApplication, bindings: Bindings, computer: Computer): Evaluation {
        if (term.args.size != 2) throw SyntaxError("$identifier requires two arguments")
        val (listTerm, indexTerm) = term.args
        val list = (listTerm as? TList)?.value
            ?: throw SyntaxError("First argument to $identifier must be a List")
        val index = (indexTerm as? TInteger)?.value?.toInt()
            ?: throw SyntaxError("Second argument to $identifier must be an Integer")
        val result = list.run { if (size < index + 1) TNil else get(index) }
        return Evaluation.applyFunction(term, bindings, result)
    }
}

class Let : Function(TSymbol("sys", "let")) {
    override fun apply(term: TApplication, bindings: Bindings, computer: Computer): Evaluation {
        if (term.args.size != 2) throw SyntaxError("$identifier requires two arguments")
        val (bindingsListTerm, termToEvaluate) = term.args
        val letBindings = (bindingsListTerm as? TList)?.value
            ?.map { (it as? TList)?.value?.apply { if (size == 2) throw syntaxError } ?: throw syntaxError }
            ?.associate { Pair(it[0] as? TSymbol ?: throw syntaxError, it[1]) }
            ?:mutableMapOf()
        return computer.evaluate(termToEvaluate, bindings + letBindings)
    }
    private val syntaxError = SyntaxError("$identifier bindings must be Lists of [Symbol Term] pairs")
}

class HttpGetFunction(private val template: FluentProducerTemplate) : Function(TSymbol("sys.net.http", "get")) {
    override fun apply(term: TApplication, bindings: Bindings, computer: Computer): Evaluation {
        if (term.args.size != 1) throw SyntaxError("$identifier requires argument")
        val url = term.args[0] as? TString ?: throw SyntaxError("Argument to $identifier must be of type String")
        val result = TString(template.to(url.value).request(String::class.java))
        return Evaluation.applyFunction(term, bindings, result)
    }
}

class GroovyScriptFunction : Function(TSymbol("sys.scripting.groovy", "eval")) {
    override fun apply(term: TApplication, bindings: Bindings, computer: Computer): Evaluation {
        checkSyntax(term.args)
        val source = extractSource(term.args)
        val scriptArgs = extractScriptArgs(term.args)
        val binding = createBinding(scriptArgs, computer, bindings)
        val shell = GroovyShell(binding)
        val result = Term.of(shell.evaluate(source.value, identifier.toString()))
        return Evaluation.applyFunction(term, bindings, result)
    }

    private fun checkSyntax(args: kotlin.collections.List<Term>) {
        if (args.size != 2) throw SyntaxError("$identifier must have two arguments")
    }

    private fun extractSource(args: kotlin.collections.List<Term>): TString {
        return args[0] as? TString ?: throw SyntaxError("First argument to $identifier must be of type String")
    }

    private fun extractScriptArgs(args: kotlin.collections.List<Term>): TKeywordMap {
        return args[1] as? TKeywordMap ?: throw SyntaxError("Second argument to $identifier must be of type KeywordMap")
    }

    private fun createBinding(args: TKeywordMap, parentStep: Computer, bindings: Bindings): Binding {
        val binding = Binding()
        args.value.forEach { keyword, term -> binding.setVariable(keyword.value.toString(), term.unwrap()) }
        binding.setVariable("invoke", TermEvaluatingClosure(parentStep, bindings))
        return binding
    }

    companion object {

        class TermEvaluatingClosure(private var computer: Computer, private val bindings: Bindings)
            : Closure<Evaluation>(null) {
            @Suppress("unused")
            fun doCall(identifier: String, vararg args: Any?) : Any {
                val symbol = TSymbol(identifier.substringBefore('/'), identifier.substringAfter('/'))
                val argsTerm = functionArgs(args)
                val invocationTerm = TApplication(symbol, argsTerm)
                return computer.evaluate(invocationTerm, bindings)
            }

            private fun functionArgs(args: Array<out Any?>) = args.map { Term.of(it) }
        }
    }
}

class UnknownSymbolException(symbol: TSymbol) : SyntaxError("Unknown symbol: $symbol")

open class SyntaxError(message: String) : RuntimeException(message)

fun main(args: Array<String>) {
    val context = DefaultCamelContext()
    context.start()
    val builtIns = listOf(
        Get(),
        Let(),
        HttpGetFunction(context.createFluentProducerTemplate()),
        GroovyScriptFunction()
    )
    val computer = Computer(builtIns, Cache())
    println(computer.evaluate(Term.parse("test/a"), mapOf(TSymbol("test", "a") to TInteger(123))))
}