import groovy.lang.Binding
import groovy.lang.Closure
import groovy.lang.GroovyObjectSupport
import groovy.lang.GroovyShell
import java.util.concurrent.CompletableFuture

/**
 * @author Ewan
 */
object Groovy {
    fun evaluate(
        symbol: Term.Value.Atom.Symbol,
        script: String,
        args: Map<String, Data>,
        options: Map<String, Data>,
        computer: Computer
    ): CompletableFuture<Term> {
        val syntheticFilename = symbol.value.toString()
        val binding = Binding(args.mapKeys { "_${it.key}" } + Pair("async", AsyncBlock(args)))
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

    override fun getProperty(property: String?): Any {
        return when(property) {
            in args.keys -> args[property]!!.value
            else -> super.getProperty(property)
        }
    }
}

object GroovyEvaluator: GroovyScriptResolver.Evaluator {
    override fun evaluate(symbol: Term.Value.Atom.Symbol, source: String, args: Map<String, Data>,
                          options: Map<String, Data>, computer: Computer): CompletableFuture<Term> {
        return Groovy.evaluate(symbol, source, args, options, computer)
    }
}

class Data(private val term: Term, private val computer: Computer) {
    @Suppress("unused")
    val value: CompletableFuture<*>
        get() = when (term) {
            is Term.Value.Atom<*> ->
                if (term == Term.Value.Atom.Nil) CompletableFuture.completedFuture(null)
                else CompletableFuture.completedFuture(term.value)
            is Term.Value.Container.List -> CompletableFuture.completedFuture(term.value.map { Data(it, computer) })
            is Term.Value.Container.Set -> CompletableFuture.completedFuture(term.value.map { Data(it, computer) }.toSet())
            is Term.Value.Container.Map -> CompletableFuture.completedFuture(term.value.map { Pair(Data(it.key, computer), Data(it.value, computer)) }.toMap())
            is Term.Value.Container.KeywordMap -> CompletableFuture.completedFuture(term.value.map { Pair(Data(it.key, computer), Data(it.value, computer)) }.toMap())
            is Term.FunctionApplication -> computer.evaluate(term).thenApply { it.result.value }
        }
}