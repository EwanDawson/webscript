import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol

/**
 * @author Ewan
 */

interface TermResolver<out To:Term> {
    fun canResolve(term: Term): Boolean
    fun resolve(term: Term, context: Context): To
}

open class SubstitutingResolver(val matcher: (Term.FunctionApplication) -> Boolean, val transform: (Term.FunctionApplication) -> Term.FunctionApplication) : TermResolver<Term.FunctionApplication> {

    override fun canResolve(term: Term) = term is Term.FunctionApplication && matcher(term)

    override fun resolve(term: Term, context: Context) = transform(term as Term.FunctionApplication)
}

class HttpResolver(private val client: Client) : TermResolver<Term.Atom.String> {
    override fun canResolve(term: Term) = term is Term.FunctionApplication &&
        term.symbol == httpFn && urlTerm(term) != Term.Atom.Nil

    override fun resolve(term: Term, context: Context): Term.Atom.String {
        val url = context.resolve(urlTerm(term as Term.FunctionApplication)) as Term.Atom.String
        return client.get(url)
    }

    private fun urlTerm(term: Term.FunctionApplication) = term.args.value[urlParameter]!!

    companion object {
        private val namespace = "sys.net.http"
        val urlParameter = Term.Atom.Keyword(Keyword.newKeyword(namespace, "url"))
        val httpFn = Term.Atom.Symbol(Symbol.newSymbol(namespace, "get")!!)
        fun forUrl(url: Term) = Term.FunctionApplication(httpFn, Term.Container.KeywordMap(mapOf(urlParameter to url)))
    }

    interface Client {
        fun get(url: Term.Atom.String): Term.Atom.String
    }
}

class GroovyScriptResolver(private val evaluator: Evaluator) : TermResolver<Term> {
    override fun canResolve(term: Term) = term is Term.FunctionApplication &&
        term.symbol == groovyFn && sourceTerm(term) != Term.Atom.Nil && argsTerm(term) != Term.Atom.Nil

    override fun resolve(term: Term, context: Context): Term {
        val sourceArg = context.resolve(sourceTerm(term as Term.FunctionApplication)) as Term.Atom.String
        val argsMap = context.resolve(argsTerm(term)) as Term.Container.KeywordMap
        val scriptArgs = argsMap.value.mapKeys { it.key.value }
        return evaluator.evaluate(term.symbol, sourceArg, scriptArgs, context)
    }

    private fun sourceTerm(term: Term.FunctionApplication) = term.args.value[sourceParameter]!!

    private fun argsTerm(term: Term.FunctionApplication) = term.args.value[argsParameter]!!

    companion object {
        private val namespace = "sys.scripting.groovy"
        val sourceParameter = Term.Atom.Keyword(Keyword.newKeyword(namespace, "source"))
        private val argsParameter = Term.Atom.Keyword(Keyword.newKeyword(namespace, "args"))
        val groovyFn = Term.Atom.Symbol(Symbol.newSymbol("sys.scripting.groovy", "eval")!!)
        fun forSourceAndArgs(source: Term, args: Term) = Term.FunctionApplication(groovyFn, Term.Container.KeywordMap(mapOf(sourceParameter to source, argsParameter to args)))
    }

    interface Evaluator {
        fun evaluate(symbol: Term.Atom.Symbol, source: Term.Atom.String, args: Map<Keyword, Term>, context: Context): Term
    }
}

class GroovyUriScriptResolver(symbol: Term.Atom.Symbol, url: Term): SubstitutingResolver(
    { (fn) -> fn == symbol },
    { fnAppl -> GroovyScriptResolver.forSourceAndArgs(HttpResolver.forUrl(url), fnAppl.args) }
)

class FunctionRenameResolver(from: Term.Atom.Symbol, to: Term.Atom.Symbol): SubstitutingResolver(
    { (fn) -> fn == from },
    { fnAppl -> Term.FunctionApplication(to, Term.Container.KeywordMap(fnAppl.args.value.mapKeys { Term.Atom.Keyword(Keyword.newKeyword(to.value.prefix, it.key.value.name))  })) }
)