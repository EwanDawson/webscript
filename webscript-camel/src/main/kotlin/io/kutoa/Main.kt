package io.kutoa

import groovy.lang.Binding
import groovy.lang.GroovyShell
import io.kutoa.Term.Companion.kmap
import io.kutoa.Term.Companion.of
import org.apache.camel.FluentProducerTemplate
import org.apache.camel.impl.DefaultCamelContext
import org.codehaus.groovy.control.CompilerConfiguration
import us.bpsm.edn.Keyword

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
typealias TError = Term.Atom.Constant.Error
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
            val binding = bindings[term]!!
            if (binding.isConstant) {
                Evaluation(term, bindings, binding, Evaluation.Operation.BIND_SYMBOL, emptyList(), mapOf(term to binding))
            } else {
                val substep = evaluate(bindings[term]!!, bindings)
                Evaluation(term, bindings, substep.result, Evaluation.Operation.BIND_SYMBOL, listOf(substep), mapOf(term to binding) + substep.dependencies)
            }
        }
        else {
            val error = UnknownSymbolException(term)
            Evaluation.bindSymbol(term, bindings, error)
        }

    private fun evaluateApplication(term: TApplication, bindings: Bindings) : Evaluation =
        cache.getOrCompute(term, bindings) {
            val subSteps = mutableListOf<Evaluation>()
            val dependencies = mutableMapOf<TSymbol, Term>()
            when {
                builtInsMap.containsKey(term.symbol) -> {
                    evaluateBuiltIn(term, builtInsMap[term.symbol]!!, bindings)
                }
                bindings.containsKey(term.symbol) -> {
                    val expansion = expandMacro(term, bindings[term.symbol] as TApplication)
                    subSteps.add(Evaluation.macroExpansion(term, bindings, expansion))
                    val evaluation = evaluateApplication(expansion, bindings)
                    dependencies[term.symbol] = bindings[term.symbol]!!
                    dependencies.putAll(evaluation.dependencies)
                    subSteps.add(evaluation)
                    if (evaluation.result.isConstant)
                        Evaluation(term, bindings, evaluation.result, Evaluation.Operation.APPLY_FUNCTION, subSteps, dependencies)
                    else {
                        val subEval = evaluate(evaluation.result, bindings)
                        dependencies.putAll(subEval.dependencies)
                        subSteps.add(subEval)
                        Evaluation(term, bindings, subEval.result, Evaluation.Operation.APPLY_FUNCTION, subSteps, dependencies)
                    }
                }
                else -> {
                    val error = UnknownSymbolException(term.symbol)
                    Evaluation.bindSymbol(term.symbol, bindings, error)
                }
            }
        }

    private fun expandMacro(source: TApplication, macro: TApplication): TApplication {
        val bindings = mutableMapOf<TSymbol, Term>()
        bindings[TSymbol("%name")] = source.symbol
        source.args.forEachIndexed { index, term -> bindings[TSymbol("%$index")] = term }
        return expandTerm(macro, bindings) as TApplication
    }

    private fun expandTerm(term: Term, bindings: Map<TSymbol, Term>) : Term {
        return when (term) {
            is Term.Atom.Constant<Any> -> term
            is Term.Atom.Constant.Error -> term
            is Term.Atom.Symbol -> bindings[term] ?: term
            is Term.Compound<Any> -> term.map { t -> expandTerm(t, bindings) }
            is Term.Application -> {
                val symbolExpansion = expandTerm(term.symbol, bindings)
                val expandedSymbol = symbolExpansion as? TSymbol ?: throw SyntaxError("Error expanding function symbol '${term.symbol}' to '$symbolExpansion':  Macro expansion of function symbol must also be a function symbol")
                TApplication(expandedSymbol, term.args.map { expandTerm(it, bindings) })
            }
        }
    }

    // TODO: This is a mess and needs tidied up (tracking of substeps and dependencies)
    private fun evaluateBuiltIn(term: TApplication, function: Function, bindings: Bindings) : Evaluation {
        val substeps = mutableListOf<Evaluation>()
        val dependencies = mutableMapOf<TSymbol, Term>()
        val resolvedArgs = term.args.map {
            if (it.isConstant) it
            else {
                val evaluation = evaluate(it, bindings)
                substeps.add(evaluation)
                dependencies.putAll(evaluation.dependencies)
                evaluation.result
            }
        }
        val evaluation : Evaluation
        evaluation = try {
            val subEvaluation = function.apply(TApplication(term.symbol, resolvedArgs), bindings, this)
            if (resolvedArgs == term.args) subEvaluation
            else {
                substeps.add(subEvaluation)
                dependencies.putAll(subEvaluation.dependencies)
                Evaluation(term, bindings, subEvaluation.result, Evaluation.Operation.APPLY_FUNCTION, substeps, dependencies)
            }
        } catch (e: Exception) {
            Evaluation.applyFunction(term, bindings, e.toTerm())
        }
        substeps.addAll(evaluation.subSteps)
        return evaluation.copy(subSteps = substeps.distinct(), dependencies = dependencies)
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
            return Evaluation(input, bindings, Term.Atom.Constant.Error(throwable), Operation.BIND_SYMBOL, emptyList(), emptyMap())
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

        fun macroExpansion(term: TApplication, bindings: Bindings, expansion: TApplication) =
            Evaluation(term, bindings, expansion, Evaluation.Operation.EXPAND_MACRO, emptyList(), mapOf(term.symbol to bindings[term.symbol]!!))
    }

    enum class Operation {
        CONSTANT,
        COMPOUND,
        BIND_SYMBOL,
        APPLY_FUNCTION,
        CACHE_HIT,
        EXPAND_MACRO
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
        val options = term.args[0] as? TMap ?: throw SyntaxError("Argument to $identifier must be a Map")
        val url = options.value[TKeyword("url")] as? TString ?: throw SyntaxError("Options argument to $identifier must include an entry with 'url'")
        var request = template.to(url.value)
        val headers = (options.value[TKeyword("headers")] as? TMap)?.unwrap()
        headers?.forEach { k, v -> request = request.withHeader(k.toString(), v) }
        val result = TString(request.request(String::class.java))
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