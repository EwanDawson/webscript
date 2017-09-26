import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.streams.asSequence
import kotlin.system.measureTimeMillis

/**
 * @author Ewan
 */

class Computer(private val cache: Cache) {

    private val log = LoggerFactory.getLogger("Computer")!!

    private val operators = listOf(CacheRetriever(cache), FunctionSymbolSubstituter) + listOf(
        HttpInvoker(CamelHttpClient, this),
        GroovyScriptInvoker(this, Groovy)
    )

    suspend fun evaluate(term: Term.FunctionApplication, context: Context): FunctionEvaluation {
        var currentTerm: Term = term
        var currentContext = context
        val operations = mutableListOf<Operation<*,*>>()
        while (currentTerm !is Term.Value<*>) {
            val operator = operators
                .find { it.matches(currentTerm, currentContext) }
                ?: throw UnresolvableTermException(currentTerm, currentContext)
            val operation = operator.operate(currentTerm, currentContext)
            if (operation !is CachedOperation) cache.put(Cache.Key(currentTerm, currentContext), operation)
            log.info(operation.toString())
            operations.add(operation)
            currentTerm = operation.outputTerm
            currentContext = operation.outputContext
        }
        val result = FunctionEvaluation(term, currentTerm, context, currentContext, operations.toList())
        if (evaluationNotRetrievedFromCache(result)) cache.put(Cache.Key(term, context), result)
        return result
    }

    private fun evaluationNotRetrievedFromCache(operations: FunctionEvaluation) =
        (operations.subOps.size == 1 && operations.subOps[0].type == "CACHED").not()

    fun shutdown() {
        Groovy.shutdown()
    }
}

data class Context(val substitutions: List<Substitution>)

data class Substitution(val from: Term.Value.Atom.Symbol, val to: Term)

sealed class Operation<out From:Term, out To:Term>(
    val type: String,
    open val inputTerm: From,
    open val outputTerm: To,
    open val inputContext: Context,
    open val outputContext: Context,
    open val subOps: List<Operation<*,*>>
) {
    fun describe(indent: Int = 0): String =
        " ".repeat(indent) +
            "$type: ${inputTerm.toEDN()} -> ${outputTerm.toEDN()}" +
            subOps.joinToString { "\n${it.describe(indent + 2)}" }
}

data class FunctionResolution(
    override val inputTerm: Term.FunctionApplication,
    override val outputTerm: Term.FunctionApplication,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term.FunctionApplication, Term.FunctionApplication>("FNRESL", inputTerm, outputTerm, inputContext, outputContext, subOps)

data class FunctionSubstitution(
    override val inputTerm: Term.FunctionApplication,
    override val outputTerm: Term,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term.FunctionApplication, Term>("FNSUBS", inputTerm, outputTerm, inputContext, outputContext, subOps)

data class FunctionInvocation(
    val invoker: String,
    override val inputTerm: Term.FunctionApplication,
    override val outputTerm: Term,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term.FunctionApplication, Term>("FNRSLN", inputTerm, outputTerm, inputContext, outputContext, subOps)

data class FunctionEvaluation(
    override val inputTerm: Term.FunctionApplication,
    override val outputTerm: Term.Value<*>,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term.FunctionApplication, Term.Value<*>>("FNEVAL", inputTerm, outputTerm, inputContext, outputContext, subOps)

data class NoOperation(
    override val inputTerm: Term,
    override val outputTerm: Term,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term, Term>("NOOPER", inputTerm, outputTerm, inputContext, outputContext, subOps) {
    constructor(term: Term, context: Context) : this(term, term, context, context, emptyList())
}

data class CachedOperation(
    val operation: Operation<*,*>
) : Operation<Term, Term>("CACHED", operation.inputTerm, operation.outputTerm, operation.inputContext, operation.outputContext, listOf(operation))

interface Operator<out From:Term, out To:Term> {
    fun matches(term: Term, context: Context): Boolean
    suspend fun operate(term: Term, context: Context): Operation<From, To>
}

class CacheRetriever(private val cache: Cache) : Operator<Term, Term> {
    override fun matches(term: Term, context: Context): Boolean {
        return cache.exists(Cache.Key(term, context))
    }

    override suspend fun operate(term: Term, context: Context): CachedOperation {
        return cache.get(Cache.Key(term, context))
    }
}

object FunctionSymbolSubstituter : Operator<Term.FunctionApplication, Term> {
    override fun matches(term: Term, context: Context): Boolean {
        return term is Term.FunctionApplication && context.substitutions.any {
            it.from == term.symbol
        }
    }

    override suspend fun operate(term: Term, context: Context): FunctionSubstitution {
        term as Term.FunctionApplication
        val substitution = context.substitutions.find { it.from == term.symbol }!!.to as Term.FunctionApplication
        val newFnApplication = Term.function(substitution.symbol, substitution.args + term.args)
        return FunctionSubstitution(term, newFnApplication, context, context, emptyList())
    }
}

object FunctionResolver : Operator<Term.FunctionApplication, Term.FunctionApplication> {
    override fun matches(term: Term, context: Context): Boolean {
        return term is Term.FunctionApplication && FunctionSymbolSubstituter.matches(term.symbol, context)
    }

    override suspend fun operate(term: Term, context: Context): FunctionResolution {
        term as Term.FunctionApplication
        val steps = mutableListOf<FunctionSubstitution>()
        var symbol = term.symbol
        while (FunctionSymbolSubstituter.matches(symbol, context)) {
            val substitution = FunctionSymbolSubstituter.operate(symbol, context)
            steps.add(substitution)
            symbol = (substitution.outputTerm as Term.FunctionApplication).symbol
        }
        return FunctionResolution(term, steps.last().outputTerm as Term.FunctionApplication, context, context, steps.toList())
    }

}

interface FunctionInvoker : Operator<Term.FunctionApplication, Term>

@Suppress("unused")
class UnresolvableTermException(term: Term, context: Context) : RuntimeException("No resolver found for ${term.toEDN()}")

fun main(args: Array<String>) = runBlocking {
    val computer = Computer(HashMapCache)
    val elapsed = measureTimeMillis {
        val inputs = produceInputs()
        val evaluations = evaluate(inputs, computer)
        for (i in 1..100) {
            val evaluation = evaluations.receive()
            println (evaluation.outputTerm.value)
            println (evaluation.describe())
        }
        evaluations.cancel()
        inputs.cancel()
    }
    println("Elapsed time: ${elapsed}ms = ${elapsed / 100.0}ms/eval")
    computer.shutdown()
}


fun produceInputs() = produce(CommonPool) {
    val inputs = listOf(
//        "(async-test{:a 1 :b 2})",
        "(add{:a 1 :b 2})",
        "(two-plus-two)"
    )
    val seq = Random().ints(0, inputs.size).asSequence().iterator()
    while(true) send(Term.parse(inputs[seq.next()]) as Term.FunctionApplication)
}

fun evaluate(inputs: ReceiveChannel<Term.FunctionApplication>, computer: Computer) = produce(CommonPool) {
    val context = Context(listOf(
        Substitution(Term.symbol("two-plus-two"),
            Term.function(GroovyScriptInvoker.groovyFn,
                listOf(Term.function(HttpInvoker.httpFn,
                    listOf(Term.string("https://gist.githubusercontent.com/EwanDawson/8f069245a235be93e3b4836b4f4fae61/raw/1ba6e295c8a6b10d1f325a952ddf4a3546bd0415/two-plus-two.groovy"))),
                    Term.parse("{}")
                )
            )
        ),
        Substitution(Term.symbol("add"),
            Term.function(GroovyScriptInvoker.groovyFn,
                listOf(Term.function(HttpInvoker.httpFn,
                    listOf(Term.string("https://gist.githubusercontent.com/EwanDawson/a5aee75d7819978b27c6b73cb5815c36/raw/44a648bdd8a766b45d825911e69db4c4125477f0/add.groovy")))
                )
            )
        ),
        Substitution(Term.symbol("async-test"),
            Term.function(GroovyScriptInvoker.groovyFn,
                listOf(Term.function(HttpInvoker.httpFn,
                    listOf(Term.string("https://gist.githubusercontent.com/EwanDawson/d89a881fce76e0f02fca7349b9f6a925/raw/835acd6e1e7369fb7907d852260152b05e25e25f/asyncTest.groovy")))
                )
            )
        )
    ))
    for (input in inputs) send(computer.evaluate(input, context))
}