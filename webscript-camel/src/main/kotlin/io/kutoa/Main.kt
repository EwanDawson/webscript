package io.kutoa

import groovy.lang.Binding
import groovy.lang.GroovyShell
import io.kutoa.Term.Companion.kmap
import io.kutoa.Term.Companion.of
import org.apache.camel.FluentProducerTemplate
import org.apache.camel.impl.DefaultCamelContext
import org.codehaus.groovy.control.CompilerConfiguration
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
                constructor(value: Number) : this(value as? BigInteger ?: BigInteger.valueOf(value.toLong()))
                override fun toString() = toEDNPretty()
            }
            data class Decimal(override val value: BigDecimal): Constant<BigDecimal>(value) {
                constructor(value: Number) : this(value as? BigDecimal ?: BigDecimal.valueOf(value.toDouble()))
                override fun toString() = toEDNPretty()
            }
            data class Character(override val value: kotlin.Char): Constant<kotlin.Char>(value) {
                override fun toString() = toEDNPretty()
            }
            data class Boolean(override val value: kotlin.Boolean): Constant<kotlin.Boolean>(value) {
                override fun toString() = toEDNPretty()
            }
            data class Keyword(override val value: us.bpsm.edn.Keyword): Constant<us.bpsm.edn.Keyword>(value) {
                constructor(prefix: kotlin.String, name: kotlin.String) : this (us.bpsm.edn.Keyword.newKeyword(prefix, name))
                constructor(name: kotlin.String) : this (us.bpsm.edn.Keyword.newKeyword(name))
                override fun toString() = toEDNPretty()
            }
            // TODO("Add Lambda atom - should be constant, allowing from closure over variables from currently scoped binding")
            override val isConstant = true
        }
        data class Error(override val value: ErrorInfo) : Atom<ErrorInfo>(value) {
            constructor(throwable: Throwable) : this(ErrorInfo(throwable))
            constructor(name: String, message: String) : this(ErrorInfo(name, message))
            override val isConstant = true
            // TODO: Create EDN representation of Error
            override fun toString() = value.toString()
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
        data class Map(override val value: kotlin.collections.Map<out TConstant<*>,Term>): Compound<kotlin.collections.Map<out TConstant<*>,Term>>(value) {
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
                is kotlin.collections.List<*> -> {
                    val list = value as kotlin.collections.List<Any?>
                    val firstTerm = list.firstOrNull()?.toTerm()
                    if (firstTerm != null && firstTerm is TSymbol) TApplication(firstTerm, list.drop(1).map(Term.Companion::of))
                    else list.toTerm()
                }
                is Set<*> -> value.toTerm()
                is Map<*, *> -> value.toTerm()
                else -> throw SyntaxError("Cannot create Term from ${value::class} '$value'")
            }
        }

        fun kmap(value: Map<String, Any?>) = TMap(value.mapKeys { TKeyword(it.key) }.mapValues { Term.of(it.value) })
    }
}

fun term(value: Any?) = Term.of(value)

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
typealias TList = Term.Compound.List
typealias TMap = Term.Compound.Map
typealias TSet = Term.Compound.Set
typealias TApplication = Term.Application

fun Any.toTerm() = Term.of(this)
fun String.toTerm() = TString(this)
fun String.parseTerm() = Term.parse(this)
fun Int.toTerm() = TInteger(this)
fun kotlin.collections.List<Any?>.toTerm() = TList(this.map { of(it) })
fun kotlin.collections.Map<*,*>.toTerm() = TMap(this.map { Pair(of(it.key) as? TConstant<*> ?: throw SyntaxError("Map key must be a Constant"), of(it.value)) }.toMap())
fun Set<Any?>.toTerm() = TSet(this.map { of(it) }.toSet())
fun Throwable.toTerm() = TError(this)

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
        return when (term) {
            is TList -> {
                term.value.forEach { subSteps.add(evaluate(it, bindings)) }
                Evaluation.compound(term, bindings, TList(subSteps.map(Evaluation::result)), subSteps)
            }
            is TSet -> {
                term.value.forEach { subSteps.add(evaluate(it, bindings)) }
                Evaluation.compound(term, bindings, TSet(subSteps.map(Evaluation::result).toSet()), subSteps)
            }
            is TMap -> {
                val result = term.map {
                    val evaluation = evaluate(it, bindings)
                    subSteps.add(evaluation)
                    evaluation.result
                }
                Evaluation.compound(term, bindings, result, subSteps)
            }
        }
    }

    private fun evaluateSymbol(term: TSymbol, bindings: Bindings) : Evaluation =
        if (bindings.containsKey(term)) {
            val substep = evaluate(bindings[term]!!, bindings)
            Evaluation.bindSymbol(term, bindings, substep.result, substep)
        }
        else {
            val error = UnknownSymbolException(term)
            Evaluation.bindSymbol(term, bindings, error)
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
                    Evaluation.bindSymbol(term.symbol, bindings, error)
                }
            }
        }

    private fun createMacroBindings(identifier: TSymbol, args: kotlin.collections.List<Term>, bindings: Bindings) : Bindings =
        bindings + mapOf(macroIdentifier to identifier, macroArgs to TList(args))

    private fun evaluateBuiltIn(term: TApplication, function: Function, bindings: Bindings) : Evaluation {
        val substeps = term.args.map { evaluate(it, bindings) }
        val evaluation : Evaluation
        evaluation = try {
            function.apply(TApplication(term.symbol, substeps.map { it.result }), bindings, this)
        } catch (e: Exception) {
            Evaluation.applyFunction(term, bindings, e.toTerm())
        }
        return evaluation.copy(subSteps = substeps + evaluation.subSteps)
    }
}

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
        fun constant(input: Term) = constant(input, emptyMap())

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

        fun applyFunction(term: TApplication, bindings: Bindings, result: Term, subInvocations: kotlin.collections.List<Evaluation> = emptyList()) =
            Evaluation(term, bindings, result, Operation.APPLY_FUNCTION, subInvocations, emptyMap())
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

class GroovyScriptFunction : Function(symbol) {
    override fun apply(term: TApplication, bindings: Bindings, computer: Computer): Evaluation {
        checkSyntax(term.args)
        val source = extractSource(term.args)
        val scriptArgs = extractScriptArgs(term.args)
        val subInvocations = mutableListOf<Evaluation>()
        val binding = createBinding(scriptArgs, computer, bindings, subInvocations)
        val compilerConfiguration = CompilerConfiguration.DEFAULT
        compilerConfiguration.scriptBaseClass = "io.kutoa.KutoaGroovyScript"
        val shell = GroovyShell(binding, compilerConfiguration)
        val result = shell.evaluate(source.value, identifier.toString())
        return Evaluation.applyFunction(term, bindings, Term.of(result), subInvocations)
    }

    private fun checkSyntax(args: kotlin.collections.List<Term>) {
        if (args.isEmpty() || args.size > 2) throw SyntaxError("$identifier must have one or two arguments")
    }

    private fun extractSource(args: kotlin.collections.List<Term>): TString {
        return args[0] as? TString ?: throw SyntaxError("First argument to $identifier must be of type String")
    }

    private fun extractScriptArgs(args: kotlin.collections.List<Term>): TMap? {
        return if (args.size != 2) null
        else args[1] as? TMap ?: throw SyntaxError("Second argument to '$symbol' must be of type Map")
    }

    private fun createBinding(args: TMap?, computer: Computer, bindings: Bindings,
                              subInvocations: MutableList<Evaluation>): Binding {
        val binding = Binding()
        val argMap = mutableMapOf<String, Any>()
        args?.value?.forEach { key, term ->
            val keyword = key as? TKeyword ?: TKeyword(key.value.toString())
            val variableName = keyword.value.toVariableName()
            val value = term.unwrap()
            argMap[variableName] = value
            binding.setVariable(variableName, value)
        }
        binding.setVariable("arg", argMap.toMap())
        binding.setVariable("__computer", computer)
        binding.setVariable("__bindings", bindings)
        binding.setVariable("__substeps", subInvocations)
        return binding
    }

    companion object {

        val symbol = TSymbol("sys.scripting.groovy", "eval")

        fun application(script: String, vararg args : Pair<String, Any?>)
            = TApplication(symbol, listOf(TString(script)) + if (args.isNotEmpty()) listOf(kmap(args.toMap())) else emptyList())

        private fun Keyword.toVariableName() : String = this.toString().replaceFirst(":", "")
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