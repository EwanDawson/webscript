import groovy.lang.Binding
import groovy.lang.GroovyShell
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.Future

/**
 * @author Ewan
 */
object Groovy {
    fun evaluate(symbol: Term.Value.Atom.Symbol, script: Term.Value.Atom.String, args: Term.Value.Container.KeywordMap, computer: Computer): Term {
        val scriptText = script.value
        val syntheticFilename = symbol.value.toString()
        val binding = Binding(args.value.map { Pair(it.key.toString(), Data(it.value, computer)) }.toMap())
        return Term.of(GroovyShell(binding).evaluate(scriptText, syntheticFilename))
    }
}

object GroovyEvaluator: GroovyScriptResolver.Evaluator {
    override fun evaluate(symbol: Term.Value.Atom.Symbol, source: Term.Value.Atom.String, args: Term.Value.Container.KeywordMap, computer: Computer): Term {
        return Groovy.evaluate(symbol, source, args, computer)
    }
}

class Data(private val term: Term, private val computer: Computer) {
    @Suppress("unused")
    val value: Future<*>
        get() = when (term) {
            is Term.Value.Atom<*> -> if (term == Term.Value.Atom.Nil) completedFuture(null) else completedFuture(term.value)
            is Term.Value.Container.List -> completedFuture(term.value.map { Data(it, computer) })
            is Term.Value.Container.Set -> completedFuture(term.value.map { Data(it, computer) }.toSet())
            is Term.Value.Container.Map -> completedFuture(term.value.map { Pair(Data(it.key, computer), Data(it.value, computer)) }.toMap())
            is Term.Value.Container.KeywordMap -> completedFuture(term.value.map { Pair(Data(it.key, computer), Data(it.value, computer)) }.toMap())
            is Term.FunctionApplication -> computer.evaluate(term)
        }
}