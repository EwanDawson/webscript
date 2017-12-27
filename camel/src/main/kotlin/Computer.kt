import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.streams.asSequence
import kotlin.system.measureTimeMillis

/**
 * A `Computer`s responsibility is to reduce a [FunctionApplication][Term.FunctionApplication] [Term] to a
 * [FunctionEvaluation], with respect to some [Context]. To do this, the client must send an [evaluate] message to the
 * `Computer`.
 *
 * To achieve this reduction, the `Computer` depends on a set of [Operator]s:
 *
 * * [CacheRetriever] to obtain any previously evaluated [FunctionApplication][Term.FunctionApplication]
 * * [FunctionSymbolSubstituter] to replace a symbol from the current `FunctionApplication` with another [Term] from
 * the evaluation [Context].
 * * [HttpInvoker] to resolve any HTTP [Terms][Term]
 * * [GroovyScriptInvoker] to resolve any Groovy script [Terms][Term]
 *
 * @author Ewan
 */
class Computer(private val cache: Cache) {

    private val log = LoggerFactory.getLogger("Computer")!!

    /**
     * The order of these operators is important; the computer tries to apply each operator in turn.
     */
    private val operators = listOf(
        CacheRetriever(cache),
        FunctionSymbolSubstituter,
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
                ?: throw UnresolvableTermException(currentTerm)
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

    /**
     * Perform any cleanup required before destroying this computer
     */
    fun shutdown() {
        Groovy.shutdown()
    }
}

/**
 * A computer always performs an operation with respect to a `Context` object. Thus the context is one of the inputs to
 * an [Operation], along with the [Term] being operated on. A context object is threaded through each operation performed
 * by the computer, thus may carry state local to the sequence of operations being performed.
 *
 * While `Context` objects are immutable, some operations may update the context, therefore the context object output
 * as the result of an operation (and thus input to the next operation) may not be the same and the input context.
 *
 * At the moment, the context object contains only a list of [Substitution]s to be used by the computer. This will be
 * extended in future to include other state also.
 */
data class Context(val substitutions: List<Substitution>)

/**
 * A mapping from a [Symbol][Term.Value.Atom.Symbol] to some [Term]
 */
data class Substitution(val from: Term.Value.Atom.Symbol, val to: Term)

/**
 * An `Operation` represents the process carried out by a [Computer] to translate one [Term] into another.
 * An operation is always carried out with respect to some [Context], which may be modified as a result of a process.
 * Thus an `Operation` may be thought of as a record of the application by the `Computer` of some function mapping
 * an input `(Term,Context)` to an output `(Term,Context)`.
 *
 * An `Operation` is a tree; if not atomic, it may consist of a number of sub-operations, which were carried out in
 * sequence in order to achieve the mapping described by the parent `Operation`. These sub-operations are represented
 * as a `List<Operation<*,*>>` property.
 *
 * Thus, [Computer.evaluate] produces a root `Operation` node, with is a tree of arbitrary breadth and depth, where the
 * leaf nodes are computation steps (also known as atomic operations).
 *
 * `Operation` is a sealed class intended to be overridden by data classes representing specific operations supported
 * by a computer.
 */
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

/**
 * An [Operation] mapping one [FunctionApplication][Term.FunctionApplication] term to another, composed of a number of
 * [FunctionSubstitution] sub-operations.
 */
data class FunctionResolution(
    override val inputTerm: Term.FunctionApplication,
    override val outputTerm: Term.FunctionApplication,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<FunctionSubstitution>
) : Operation<Term.FunctionApplication, Term.FunctionApplication>("FNRESL", inputTerm, outputTerm, inputContext, outputContext, subOps)

/**
 * An [Operation] mapping a [FunctionApplication][Term.FunctionApplication] term to some other [Term], via a
 * [Substitution]. This is an atomic operation (i.e. it has no sub-operations).
 */
data class FunctionSubstitution(
    override val inputTerm: Term.FunctionApplication,
    override val outputTerm: Term,
    override val inputContext: Context,
    override val outputContext: Context
) : Operation<Term.FunctionApplication, Term>("FNSUBS", inputTerm, outputTerm, inputContext, outputContext, emptyList())

/**
 * An [Operation] mapping a [FunctionApplication][Term.FunctionApplication] term to some other [Term], via the
 * invocation of some user function.
 */
data class FunctionInvocation(
    val invoker: String,
    override val inputTerm: Term.FunctionApplication,
    override val outputTerm: Term,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term.FunctionApplication, Term>("FNRSLN", inputTerm, outputTerm, inputContext, outputContext, subOps)

/**
 * An [Operation] mapping a [FunctionApplication][Term.FunctionApplication] term to a [Value][Term.Value] term, via
 * the evaluation of some user function.
 */
data class FunctionEvaluation(
    override val inputTerm: Term.FunctionApplication,
    override val outputTerm: Term.Value<*>,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term.FunctionApplication, Term.Value<*>>("FNEVAL", inputTerm, outputTerm, inputContext, outputContext, subOps)

/**
 * The identity [Operation].
 */
data class NoOperation(
    override val inputTerm: Term,
    override val outputTerm: Term,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term, Term>("NOOPER", inputTerm, outputTerm, inputContext, outputContext, subOps) {
    constructor(term: Term, context: Context) : this(term, term, context, context, emptyList())
}

/**
 * An previously executed [Operation] that has been retrieved from the operation cache.
 */
data class CachedOperation(
    private val operation: Operation<*,*>
) : Operation<Term, Term>("CACHED", operation.inputTerm, operation.outputTerm, operation.inputContext, operation.outputContext, listOf(operation))

/**
 * Represents a unit of computation capable of transforming some input `(Term,Context)` into an output
 * `(Term,Context)` (also known as an [Operation]
 *
 * You can tell if an operator can operate on a given term by sending it an [Operator.matches] message.
 *
 * Each operator has a corresponding [Operation]. You can perform the operator's operation by sending it an
 * [Operator.operate] message. This is the natural way to create [Operation] objects.
 *
 * @see Term
 * @see Context
 * @see Operation
 */
interface Operator<out From:Term, out To:Term> {
    fun matches(term: Term, context: Context): Boolean
    suspend fun operate(term: Term, context: Context): Operation<From, To>
}

/**
 * An [Operator] that matches [Term]s that have already been operated on, and thus have the resulting [Operation]
 * stored in the [Cache].
 *
 * This operator produces a [CachedOperation].
 */
class CacheRetriever(private val cache: Cache) : Operator<Term, Term> {
    override fun matches(term: Term, context: Context): Boolean {
        return cache.exists(Cache.Key(term, context))
    }

    override suspend fun operate(term: Term, context: Context): CachedOperation {
        return cache.get(Cache.Key(term, context))
    }
}

/**
 * An [Operator] that matches [Term]s that have a [Substitution] in the [Context]. The `operate` method returns a
 * [FunctionSubstitution] object representing the mapping from the input [Term] to output [Term] as found in the
 * [Context].
 */
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
        return FunctionSubstitution(term, newFnApplication, context, context)
    }
}

/**
 * Given a [Term], this functions repeatedly executes a [FunctionSymbolSubstituter] operation, until no more
 * [Substitution]s may be applied. This results in a [FunctionResolution] operation, representing a chain of applied
 * [Substitution]s.
 */
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

/**
 * An [Operator] that operated on a [FunctionApplication][Term.FunctionApplication] to produce a [Term], generally via
 * the execution of some user function, is a `FunctionInvoker`.
 */
interface FunctionInvoker : Operator<Term.FunctionApplication, Term>

/**
 * When a [Computer] cannot find an [Operator] that [matches][Operator.matches] the current [Term], it throws
 * an `UnresolvableTermException`, and evaluation of the term ceases.
 */
class UnresolvableTermException(term: Term) : RuntimeException("No resolver found for ${term.toEDN()}")

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