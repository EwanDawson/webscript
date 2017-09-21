import groovy.lang.Binding
import groovy.lang.Closure
import groovy.lang.GroovyObjectSupport
import groovy.lang.GroovyShell
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

/**
 * @author Ewan
 */
object Groovy {
    fun evaluate(
        symbol: Term.Value.Atom.Symbol,
        script: String,
        args: Map<String, Data>,
        computer: Computer
    ): Any? {
        val syntheticFilename = symbol.value.toString()
        val binding = Binding(args
            .mapKeys { "_${it.key}" }
            + Pair("async", AsyncBlock(args))
        )
        val shell = GroovyShell(binding)
        val wrappedScript = args
            .map { "def get${it.key.capitalize()}() { _${it.key}.value.get() }" }
            .joinToString(separator = "\n")
            .plus("\n$script")
        val result = shell.evaluate(wrappedScript, syntheticFilename)
        return if (result is CompletableFuture<*>) result.thenApply { Term.of(it) }
        else CompletableFuture.completedFuture(Term.of(result))
    }
}

class AsyncBlock(private val args: Map<String, Data>): GroovyObjectSupport() {
    @Suppress("unused")
    fun call(block: Closure<*>) {
        block.delegate = this
        block.resolveStrategy = Closure.DELEGATE_ONLY
        block.call()
    }

    override fun getProperty(property: String?): Any? {
        return when(property) {
            in args.keys -> args[property]?.value
            else -> super.getProperty(property)
        }
    }
}

class GroovyEvaluator: GroovyScriptInvoker.GroovyScriptEvaluator {
    private val argEvaluations = mutableListOf<FunctionEvaluation>()
    override fun evaluate(
        symbol: Term.Value.Atom.Symbol,
        source: Term.Value.Atom.String,
        args: Term.Value.Container.Map,
        context: Context,
        computer: Computer
    ): ScriptEvaluationResult {
        val argsData = args.value.map {
            Pair((it.key as Term.Value.Atom.Keyword).value.name,
                Data(it.value, context, computer, argEvaluations)
            )
        }.toMap()
        val result = (Groovy.evaluate(symbol, source.value, argsData, computer) as CompletableFuture<*>).get()
        return ScriptEvaluationResult(Term.of(result), argEvaluations.toList())
    }
}

data class ScriptEvaluationResult(val result: Term, val argEvaluations: List<FunctionEvaluation>)

class Data(
    private val term: Term,
    private val context: Context,
    private val computer: Computer,
    private val evaluations: MutableList<FunctionEvaluation>
) {
    @Suppress("unused")
    val value: Any?
        get() = Supplier {
            when (term) {
                is Term.Value.Atom<*> -> if (term == Term.Value.Atom.Nil) null else term.value
                is Term.Value.Container.List -> term.value.map { data(it) }
                is Term.Value.Container.Set -> term.value.map { data(it) }.toSet()
                is Term.Value.Container.Map -> term.value.map { Pair(data(it.key), data(it.value)) }.toMap()
                is Term.Value.Container.KeywordMap -> term.value.map { Pair(data(it.key), data(it.value)) }.toMap()
                is Term.FunctionApplication -> {
                    val evaluation = computer.evaluate(term, context)
                    evaluations.add(evaluation)
                    data(evaluation.outputTerm)
                }
            }
        }
    private fun data(term: Term) = Data(term, context, computer, evaluations)
}

class GroovyScriptInvoker(private val computer: Computer, private val evaluator: GroovyScriptEvaluator) : FunctionInvoker {
    override fun matches(term: Term, context: Context): Boolean {
        return term is Term.FunctionApplication && term.symbol == groovyFn && term.args.size == 2
    }

    override fun operate(term: Term, context: Context): FunctionInvocation {
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
        val evaluation = evaluator.evaluate(term.symbol, source, args, context, computer)
        steps.addAll(evaluation.argEvaluations)
        return FunctionInvocation(namespace, term, evaluation.result, context, context, steps.toList())
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
            computer: Computer
        ): ScriptEvaluationResult
    }
}

