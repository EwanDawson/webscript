import groovy.lang.Binding
import groovy.lang.Closure
import groovy.lang.GroovyShell
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.function.Supplier

/**
 * @author Ewan
 */

object Groovy: GroovyScriptInvoker.GroovyScriptEvaluator {
    private val executors = Executors.newFixedThreadPool(10)
    fun shutdown() { executors.shutdown() }
    override fun evaluate(
        symbol: Term.Value.Atom.Symbol,
        source: Term.Value.Atom.String,
        args: Term.Value.Container.Map,
        context: Context,
        computer: Computer,
        evaluations: SendChannel<FunctionEvaluation>
    ): ReceiveChannel<Term> {
        val argsData = args.value.map {
            Pair((it.key as Term.Value.Atom.Keyword).value.name,
                Data(it.value, context, computer, evaluations)
            )
        }.toMap()
        val syntheticFilename = symbol.value.toString()
        val binding = Binding()
        val scriptPrefix = argsData
            .map { Pair(ArgumentClosure.blocking(it.key, it.value, binding), ArgumentClosure.async(it.key, it.value, binding)) }
            .map { Pair(it.first.groovyDef, it.second.groovyDef) }
            .map { it.toList().joinToString(separator = "\n") }
            .joinToString(separator = "\n")
        val shell = GroovyShell(binding)
        val returnChannel = Channel<Term>()
        val supplier = Supplier {
            try {
                shell.evaluate("$scriptPrefix\n\n${source.value}", syntheticFilename)
            } finally {
                evaluations.close()
            }
        }
        CompletableFuture.supplyAsync(supplier, executors).thenAccept {
            runBlocking { returnChannel.send(Term.of(it)) }
        }
        return returnChannel
    }
}

internal abstract class ArgumentClosure(private val name: String, owner: Any): Closure<Any?>(owner) {
    protected abstract val type: String
    internal val bindingName by lazy { "_${name}_$type" }
    internal abstract val groovyDef: String
    companion object {
        fun blocking(name: String, value: Data, binding: Binding): BlockingArgumentClosure {
            val closure = BlockingArgumentClosure(name, binding, value)
            binding.setVariable(closure.bindingName, closure)
            return closure
        }
        fun async(name: String, value: Data, binding: Binding): AsyncArgumentClosure {
            val closure = AsyncArgumentClosure(name, binding, value)
            binding.setVariable(closure.bindingName, closure)
            return closure
        }
    }
}

@Suppress("unused")
internal class BlockingArgumentClosure(name: String, owner: Any, private val arg: Data): ArgumentClosure(name, owner) {
    override val type = "blocking"
    override val groovyDef by lazy { "def get${name.capitalize()}() { $bindingName() }" }
    fun doCall(): Any? {
        return runBlocking { arg.getValue() }
    }
}

@Suppress("unused")
internal class AsyncArgumentClosure(name: String, owner: Any, private val arg: Data) : ArgumentClosure(name, owner) {
    override val type = "async"
    override val groovyDef by lazy { "def $name(Closure block) { $bindingName(this, block) }" }
    fun doCall(caller:Any, block: Closure<*>) {
        block.delegate = caller
        block.resolveStrategy = Closure.DELEGATE_FIRST
        arg.async(block)
    }
}

class Data(
    private val term: Term,
    private val context: Context,
    private val computer: Computer,
    private val evaluations: SendChannel<FunctionEvaluation>
) {
    suspend fun getValue(): Any? {
        return when (term) {
            is Term.Value.Atom<*> -> if (term == Term.Value.Atom.Nil) null else term.value
            is Term.Value.Container.List -> term.value.map { data(it) }
            is Term.Value.Container.Set -> term.value.map { data(it) }.toSet()
            is Term.Value.Container.Map -> term.value.map { Pair(data(it.key), data(it.value)) }.toMap()
            is Term.Value.Container.KeywordMap -> term.value.map { Pair(data(it.key), data(it.value)) }.toMap()
            is Term.FunctionApplication -> {
                val evaluation = computer.evaluate(term, context)
                evaluations.send(evaluation)
                data(evaluation.outputTerm)
            }
        }
    }
    fun async(callback: Closure<*>) { launch(CommonPool) { callback.call(getValue()) } }
    private fun data(term: Term) = Data(term, context, computer, evaluations)
}

class GroovyScriptInvoker(private val computer: Computer, private val evaluator: GroovyScriptEvaluator) : FunctionInvoker {
    override fun matches(term: Term, context: Context): Boolean {
        return term is Term.FunctionApplication && term.symbol == groovyFn && term.args.size == 2
    }

    override suspend fun operate(term: Term, context: Context): FunctionInvocation {
        term as Term.FunctionApplication
        val steps = mutableListOf<Operation<*,*>>()
        val sourceTerm = term.args[0]
        val source = when (sourceTerm) {
            is Term.Value<*> -> sourceTerm
            is Term.FunctionApplication -> {
                val evaluation = computer.evaluate(sourceTerm, context)
                steps.add(evaluation)
                evaluation.outputTerm
            }
        } as? Term.Value.Atom.String ?: throw IllegalArgumentException("Script source must be of type String")
        val argsTerm = term.args[1]
        val args = when (argsTerm) {
            is Term.Value<*> -> argsTerm
            is Term.FunctionApplication -> {
                val evaluation = computer.evaluate(argsTerm, context)
                steps.add(evaluation)
                evaluation.outputTerm
            }
        } as? Term.Value.Container.Map ?: throw IllegalArgumentException("Script args must be of type KeywordMap")
        val subEvaluations = Channel<FunctionEvaluation>()
        val evaluation = evaluator.evaluate(term.symbol, source, args, context, computer, subEvaluations)
        subEvaluations.consumeEach { steps.add(it) }
        return FunctionInvocation(namespace, term, evaluation.receive(), context, context, steps.toList())
    }

    companion object {
        private val namespace = "sys.scripting.groovy"
        val groovyFn = Term.symbol(namespace, "eval")
    }

    interface GroovyScriptEvaluator {
        fun evaluate(
            symbol: Term.Value.Atom.Symbol,
            source: Term.Value.Atom.String,
            args: Term.Value.Container.Map,
            context: Context,
            computer: Computer,
            evaluations: SendChannel<FunctionEvaluation>
        ): ReceiveChannel<Term>
    }
}

