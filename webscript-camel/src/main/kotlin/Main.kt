import groovy.lang.Binding
import groovy.lang.Closure
import groovy.lang.GroovyShell
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.cache.CacheConstants
import org.apache.camel.main.Main
import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import us.bpsm.edn.parser.Parsers
import us.bpsm.edn.printer.Printers
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

fun main(args: Array<String>) {
    val main = Main()
    val evaluators = listOf(
        HttpGetFunction(),
        GroovyScriptFunction()
    )
    main.addRouteBuilder(Computer(evaluators))
    main.run()
}

enum class Operation {
    NO_OP,
    REIFY_VALUE,
    BIND_SYMBOL,
    APPLY_FUNCTION,
    CACHE_HIT
}

typealias TValue<T> = Term.Value<T>
typealias TAtom<T> = Term.Value.Atom<T>
typealias TConstant<T> = Term.Value.Atom.Constant<T>
typealias TBool = Term.Value.Atom.Constant.Bool
typealias TChar = Term.Value.Atom.Constant.Char
typealias TDecimal = Term.Value.Atom.Constant.Decimal
typealias TInt = Term.Value.Atom.Constant.Int
typealias TKeyword = Term.Value.Atom.Constant.Keyword
typealias TNil = Term.Value.Atom.Constant.Nil
typealias TString = Term.Value.Atom.Constant.String
typealias TSymbol = Term.Value.Atom.Symbol
typealias TContainer<T> = Term.Value.Container<T>
typealias TKeywordMap = Term.Value.Container.KeywordMap
typealias TList = Term.Value.Container.List
typealias TMap = Term.Value.Container.Map
typealias TSet = Term.Value.Container.Set
typealias TFunctionApplication = Term.FunctionApplication

typealias Bindings = MutableMap<TSymbol, TValue<*>>

const val OPERATION_HEADER = "OPERATION"
const val BINDINGS_HEADER = "BINDINGS"
const val STEPS_HEADER = "STEPS"

val Exchange.inTerm: Term
    get() = `in`.getBody(Term::class.java)

var Exchange.outTerm: TValue<*>
    get() = out.getBody(TConstant::class.java)
    set(value) = out.setBody(value, TConstant::class.java)

var Exchange.operation: Operation
    get() = out.getHeader(OPERATION_HEADER, Operation::class.java)
    set(value) = out.setHeader(OPERATION_HEADER, value)

@Suppress("UNCHECKED_CAST")
val Exchange.bindings: Bindings
    get() = `in`.getHeader(BINDINGS_HEADER) as Bindings

@Suppress("UNCHECKED_CAST")
val Exchange.steps: MutableList<Exchange>
    get() = out.headers.computeIfAbsent(STEPS_HEADER, { _ -> mutableListOf<Exchange>()}) as MutableList<Exchange>

fun Exchange.evaluateSubTerm(term: Term) : TValue<*> {
    if (term.isConstant) return term as TConstant<*>
    val subStep = this.context.createFluentProducerTemplate()
        .withHeader(BINDINGS_HEADER, bindings)
        .withBody(term)
        .to(Computer.COMPUTER_URI)
        .send()
    steps += subStep
    return subStep.outTerm
}

@Suppress("UNCHECKED_CAST")
val Exchange.bindingsApplied: MutableSet<Pair<TSymbol, Term>>
    get() = out.headers.computeIfAbsent("BINDINGS_APPLIED") { _ -> mutableSetOf<Pair<TSymbol, Term>>() } as MutableSet<Pair<TSymbol, Term>>

fun Exchange.bindSymbol(symbol: TSymbol) : TValue<*> {
    return if (bindings.containsKey(symbol)) {
        val substitution = bindings[symbol]!!
        bindingsApplied += symbol to substitution
        substitution
    }
    else throw UnknownSymbolException(symbol)
}

val Exchange.cacheKey : TSet
    get() = Term.set(bindingsApplied + inTerm)

class Computer(builtIns: List<Function>) : RouteBuilder() {

    private val producer = context.createFluentProducerTemplate()!!

    private val builtInsMap = builtIns.fold(mapOf()) { map: Map<TSymbol, Function>, evaluator ->
        map + (evaluator.identifier to evaluator)
    }

    override fun configure() {

        from(COMPUTER_URI)
            .process { step ->

                val inputTerm = step.inTerm

                val (outputTerm: TValue<*>, operation: Operation) = when(inputTerm) {

                    is TAtom<*> -> when (inputTerm) {
                        is TConstant<*> -> inputTerm to Operation.NO_OP
                        is TSymbol -> {
                            val substitution = step.bindSymbol(inputTerm)
                            if (substitution.isConstant) substitution to Operation.BIND_SYMBOL
                            else throw SyntaxError("Symbol $inputTerm is bound to a non-constant term")
                        }
                    }

                    is TContainer<*> -> when {
                        inputTerm.isConstant -> inputTerm to Operation.NO_OP
                        else -> {
                            val result = when (inputTerm) {
                                is TList -> Term.list(inputTerm.value.map(step::evaluateSubTerm))
                                is TSet -> Term.set(inputTerm.value.map(step::evaluateSubTerm).toSet())
                                is TMap -> Term.map(inputTerm.value
                                                        .mapKeys { step.evaluateSubTerm(it.key) }
                                                        .mapValues { step.evaluateSubTerm(it.value) })
                                is TKeywordMap ->
                                    Term.keywordMap(inputTerm.value.mapValues { step.evaluateSubTerm(it.value) })
                            }
                            assert(result.isConstant)
                            result to Operation.REIFY_VALUE
                        }
                    }

                    is TFunctionApplication -> {
                        var symbol = inputTerm.symbol

                        val cachedResult = producer.to(CACHE_URI)
                            .withHeader(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_GET)
                            .withHeader(CacheConstants.CACHE_KEY, step.cacheKey)
                            .request(TValue::class.java)

                        if (cachedResult != null) {
                            assert(cachedResult.isConstant)
                            cachedResult to Operation.CACHE_HIT
                        }
                        else {
                            while (step.bindings.containsKey(symbol)) {
                                symbol = step.bindSymbol(symbol) as? TSymbol
                                    ?: throw SyntaxError("Illegal binding of function identifier to a non-symbol term")
                            }

                            // TODO("Implement macros")

                            if (builtInsMap.containsKey(symbol)) {
                                val reifiedArgs = inputTerm.args.map(step::evaluateSubTerm)
                                val result = builtInsMap[symbol]!!.apply(reifiedArgs, step)
                                assert(result.isConstant)
                                result to Operation.APPLY_FUNCTION
                            }
                            else throw UnknownSymbolException(symbol)
                        }
                    }
                }

                producer.to(CACHE_URI)
                    .withHeader(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_ADD)
                    .withHeader(CacheConstants.CACHE_KEY, step.cacheKey)
                    .withBody(outputTerm)
                    .send()

                assert(outputTerm.isConstant)
                step.outTerm = outputTerm
                step.operation = operation
            }
    }

    companion object {
        const val COMPUTER_URI = "seda:computer"
        const val CACHE_URI = "cache:computer"
    }
}

abstract class Function(val identifier: TSymbol) {
    // TODO("Allow functions to return lambdas")
    abstract fun apply(args: List<TValue<*>>, parentStep: Exchange) : TValue<*>
}

class HttpGetFunction : Function(identifier) {

    override fun apply(args: List<TValue<*>>, parentStep: Exchange): TConstant<*> {
        if (args.size != 1) throw SyntaxError("Calls to $identifier must have 1 argument")
        val url = args[0] as? TString ?: throw SyntaxError("Argument to $identifier must be of type String")
        val producer = parentStep.context.createFluentProducerTemplate()
        return Term.string(producer.to(url.value).request(String::class.java))
    }

    companion object {
        val identifier = Term.symbol("sys.net.http", "get")
    }
}

class GroovyScriptFunction : Function(identifier) {

    override fun apply(args: List<TValue<*>>, parentStep: Exchange): TValue<*> {
        checkSyntax(args)
        val source = extractSource(args)
        val scriptArgs = extractScriptArgs(args)
        val binding = createBinding(scriptArgs, parentStep)
        val shell = GroovyShell(binding)
        val result = shell.evaluate(source.value, identifier.toString())
        return Term.of(result) as? TValue<*> ?: throw BadReturnValueException("Groovy script must evaluate to a Value")
    }

    private fun checkSyntax(args: List<TValue<*>>) {
        if (args.isEmpty()) throw SyntaxError("Calls to $identifier must have at least 1 argument")
        if (args.size > 2) throw SyntaxError("Calls to $identifier must have no more that 2 arguments")
    }

    private fun extractSource(args: List<TValue<*>>): TString {
        return args[0] as? TString ?: throw SyntaxError("First argument to $identifier must be of type String")
    }

    private fun extractScriptArgs(args: List<TValue<*>>): TKeywordMap {
        val argsTerm = if (args.size > 1) args[1] else null
        return if (argsTerm != null) argsTerm as? TKeywordMap ?: throw SyntaxError("Second argument to $identifier must be of type KeywordMap")
        else TKeywordMap.EMPTY
    }

    private fun createBinding(args: TKeywordMap, parentStep: Exchange): Binding {
        val binding = Binding()
        args.value.forEach { keyword, term -> binding.setVariable(keyword.value.toString(), term.unwrap()) }
        binding.setVariable("invoke", TermEvaluatingClosure(parentStep))
        return binding
    }

    companion object {

        val identifier = Term.symbol("sys.scripting.groovy", "eval")

        class TermEvaluatingClosure(private var parentStep: Exchange) :Closure<Any>(null) {
            @Suppress("unused")
            fun doCall(identifier: String, vararg args: Any?) : Any {
                val symbol = Term.symbol(identifier)
                val argsTerm = functionArgs(args)
                val invocationTerm = Term.function(symbol, argsTerm)
                val result = parentStep.evaluateSubTerm(invocationTerm)
                return result.unwrap()
            }

            private fun functionArgs(args: Array<out Any?>) = args.map { Term.of(it) }
        }
    }
}

@Suppress("unused", "MemberVisibilityCanBePrivate")
sealed class Term {
    sealed class Value<out T:Any>(open val value: T): Term() {
        sealed class Atom<out T:Any>(override val value: T) : Value<T>(value) {
            sealed class Constant<out T:Any>(override val value: T) : Atom<T>(value) {
                object Nil : Constant<Nil>(Nil)
                data class String(override val value: kotlin.String): Constant<kotlin.String>(value)
                data class Int(override val value: BigInteger): Constant<BigInteger>(value)
                data class Decimal(override val value: BigDecimal): Constant<BigDecimal>(value)
                data class Char(override val value: kotlin.Char): Constant<kotlin.Char>(value)
                data class Bool(override val value: Boolean): Constant<Boolean>(value)
                data class Keyword(override val value: us.bpsm.edn.Keyword): Constant<us.bpsm.edn.Keyword>(value)
                override val isConstant = true
            }
            data class Symbol(override val value: us.bpsm.edn.Symbol): Atom<us.bpsm.edn.Symbol>(value) {
                override val isConstant = true
            }
            override fun unwrap() = value
        }
        sealed class Container<out T:Any>(override val value: T) : Value<T>(value) {
            data class List(override val value: kotlin.collections.List<Term>): Container<kotlin.collections.List<Term>>(value) {
                override val isConstant get() = value.all { it.isConstant }
                override fun unwrap() = value.map { it.unwrap() }
            }
            data class Set(override val value: kotlin.collections.Set<Term>): Container<kotlin.collections.Set<Term>>(value) {
                override val isConstant get() = value.all { it.isConstant }
                override fun unwrap() = value.map { it.unwrap() }.toSet()
            }
            data class Map(override val value: kotlin.collections.Map<Term,Term>): Container<kotlin.collections.Map<Term,Term>>(value) {
                override val isConstant get() = value.values.all { it.isConstant }
                override fun unwrap() = value.map { Pair(it.key.unwrap(), it.value.unwrap()) }.toMap()
            }
            data class KeywordMap(override val value: kotlin.collections.Map<TKeyword, Term>): Container<kotlin.collections.Map<TKeyword, Term>>(value) {
                override val isConstant get() = value.values.all { it.isConstant }
                override fun unwrap() = value.map { Pair(it.key.unwrap(), it.value.unwrap()) }.toMap()
                companion object {
                    val EMPTY = KeywordMap(mapOf())
                }
            }
        }
    }
    data class FunctionApplication(val symbol: TSymbol, val args: List<Term> = emptyList()): Term() {
        override val isConstant get() = false
        override fun unwrap() = LinkedList<Any?>(listOf(symbol.unwrap()) +  args.map { it.unwrap() })
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
                is Map<*, *> -> map(value)
                is List<*> -> {
                    if (value.isEmpty()) throw SyntaxError("Bad function application")
                    val identifier = value[0] as? Symbol ?: throw SyntaxError("Bad function application")
                    function(identifier, value.drop(1).map { of(it) })
                }
                else -> throw SyntaxError("Cannot create Term from ${value::class} '$value'")
            }
        }

        fun function(symbol: Symbol, args: List<Any?>) = TFunctionApplication(symbol(symbol), args.map { of(it) })
        fun function(symbol: Value.Atom.Symbol, args: List<Term>) = TFunctionApplication(symbol, args)
        fun symbol(prefix: String, name: String) = TSymbol(Symbol.newSymbol(prefix, name))
        fun symbol(name: String) = TSymbol(
            Symbol.newSymbol(name.replaceAfter("/", ""), name.replaceBefore("/", "")))

        fun symbol(symbol: Symbol) = TSymbol(symbol)
        fun string(value: String) = TString(value)
        fun int(value: Int) = TInt(BigInteger.valueOf(value.toLong()))
        fun int(value: Long) = TInt(BigInteger.valueOf(value))
        fun int(value: BigInteger) = TInt(value)
        fun decimal(value: Float) = TDecimal(BigDecimal.valueOf(value.toDouble()))
        fun decimal(value: Double) = TDecimal(BigDecimal.valueOf(value))
        fun decimal(value: BigDecimal) = TDecimal(value)
        fun char(value: Char) = TChar(value)
        fun keyword(keyword: Keyword) = TKeyword(keyword)
        fun keyword(prefix: String, name: String) = TKeyword(Keyword.newKeyword(prefix, name))
        fun keyword(name: String) = TKeyword(Keyword.newKeyword(name))
        fun keywordMap(entries: Map<TKeyword, Term>) = Term.Value.Container.KeywordMap(entries)
        fun list(value: List<Any?>) = TList(value.map { of(it) })
        fun map(value: Map<*, *>) = TMap(value.map { Pair(of(it.key), of(it.value)) }.toMap())
        fun set(value: Set<Any?>) = TSet(value.map { of(it) }.toSet())
        fun bool(value: Boolean) = TBool(value)
        val nil = TNil
    }
}

class UnknownSymbolException(symbol: TSymbol) : SyntaxError("Unknown symbol: $symbol")

class BadReturnValueException(s: String) : RuntimeException(s)

open class SyntaxError(message: String) : RuntimeException(message)