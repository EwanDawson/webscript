import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol

/**
 * @author Ewan
 */

interface TermResolver<out To:Term> {
    fun canResolve(term: Term): Boolean
    fun resolve(term: Term, context: Context): To
}

class SubstitutingResolver(private val function: Term.Atom.Function, private val replacement: Term.Atom.Function) : TermResolver<Term.Atom.Function> {

    override fun canResolve(term: Term) = term == function

    override fun resolve(term: Term, context: Context): Term.Atom.Function {
        return if (term == function) replacement else throw IllegalArgumentException("Expected term '$function'")
    }
}

class HttpResolver(private val client: Client) : TermResolver<Term.Atom.String> {
    override fun canResolve(term: Term) = term is Term.FunctionApplication &&
        term.function == httpFn && urlTerm(term) != Term.Atom.Nil

    override fun resolve(term: Term, context: Context): Term.Atom.String {
        val url = context.resolve(urlTerm(term as Term.FunctionApplication)) as Term.Atom.String
        return client.get(url)
    }

    private fun urlTerm(term: Term.FunctionApplication) = term.args.value[urlParameter]!!

    companion object {
        private val namespace = "sys.net.http"
        private val urlParameter = Term.Atom.Keyword(Keyword.newKeyword(namespace, "url"))
        val httpFn = Term.Atom.Function(Symbol.newSymbol(namespace, "get")!!)
    }

    interface Client {
        fun get(url: Term.Atom.String): Term.Atom.String
    }
}

class GroovyScriptResolver(private val evaluator: Evaluator) : TermResolver<Term> {
    override fun canResolve(term: Term) = term is Term.FunctionApplication &&
        term.function == groovyFn && sourceTerm(term) != Term.Atom.Nil && argsTerm(term) != Term.Atom.Nil

    override fun resolve(term: Term, context: Context): Term {
        val sourceArg = context.resolve(sourceTerm(term as Term.FunctionApplication)) as Term.Atom.String
        val argsMap = context.resolve(argsTerm(term)) as Term.Container.Map
        val scriptArgs = argsMap.value.mapKeys { (it.key as Term.Atom.Keyword).value }
        return evaluator.evaluate(term.function, sourceArg, scriptArgs, context)
    }

    private fun sourceTerm(term: Term.FunctionApplication) = term.args.value[sourceParameter]!!

    private fun argsTerm(term: Term.FunctionApplication) = term.args.value[argsParameter]!!

    companion object {
        private val namespace = "sys.scripting.groovy"
        private val sourceParameter = Term.Atom.Keyword(Keyword.newKeyword(namespace, "source"))
        private val argsParameter = Term.Atom.Keyword(Keyword.newKeyword(namespace, "args"))
        val groovyFn = Term.Atom.Function(Symbol.newSymbol("sys.scripting.groovy", "eval")!!)
    }

    interface Evaluator {
        fun evaluate(function: Term.Atom.Function, source: Term.Atom.String, args: Map<Keyword, Term>, context: Context): Term
    }
}
