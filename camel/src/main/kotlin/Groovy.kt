import groovy.lang.Binding
import groovy.lang.GroovyShell
import us.bpsm.edn.Keyword

/**
 * @author Ewan
 */
object Groovy {
    fun evaluate(symbol: Term.Atom.Symbol, script: Term.Atom.String, args: Map<Keyword, Term>, context: Context): Term {
        val scriptText = script.value
        val syntheticFilename = symbol.value.toString()
        val binding = Binding(args.map { Pair(it.key.toString(), Data(it.value, context)) }.toMap())
        return Term.of(GroovyShell(binding).evaluate(scriptText, syntheticFilename))
    }
}

object GroovyEvaluator: GroovyScriptResolver.Evaluator {
    override fun evaluate(symbol: Term.Atom.Symbol, source: Term.Atom.String, args: Map<Keyword, Term>, context: Context): Term {
        return Groovy.evaluate(symbol, source, args, context)
    }
}

class Data(private val term: Term, private val context: Context) {
    @Suppress("unused")
    val value: Any?
        get() = when (term) {
            is Term.Atom<*> -> if (term == Term.Atom.Nil) null else term.value
            is Term.Container<*> -> when (term) {
                is Term.Container.List -> term.value.map { Data(it, context) }
                is Term.Container.Set -> term.value.map { Data(it, context) }.toSet()
                is Term.Container.Map -> term.value.map { Pair(Data(it.key, context), Data(it.value, context)) }.toMap()
                is Term.Container.KeywordMap -> term.value.map { Pair(Data(it.key, context), Data(it.value, context)) }.toMap()
            }
            is Term.FunctionApplication -> context.resolve(term)
        }
}