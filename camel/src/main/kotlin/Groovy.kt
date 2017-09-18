import groovy.lang.Binding
import groovy.lang.GroovyShell
import java.util.concurrent.CompletableFuture

/**
 * @author Ewan
 */
object Groovy {
    fun evaluate(symbol: Term.Value.Atom.Symbol, script: String, args: Map<String, Data>, computer: Computer): Term {
        val syntheticFilename = symbol.value.toString()
        val binding = Binding(args)
        return Term.of(GroovyShell(binding).evaluate(script, syntheticFilename))
    }
}

object GroovyEvaluator: GroovyScriptResolver.Evaluator {
    override fun evaluate(symbol: Term.Value.Atom.Symbol, source: String, args: Map<String, Data>, computer: Computer): Term {
        return Groovy.evaluate(symbol, source, args, computer)
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