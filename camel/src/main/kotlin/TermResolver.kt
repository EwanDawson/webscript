import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol

/**
 * @author Ewan
 */

interface TermResolver<out To:Term> {
    fun canResolve(term: Term): Boolean
    fun resolve(term: Term, computer: Computer): To
}

fun resolveToStringTerm(term: Term, computer: Computer): Term.Value.Atom.String {
    return when (term) {
        is Term.Value.Atom.String -> term
        is Term.FunctionApplication -> resolveToStringTerm(computer.evaluate(term).get(), computer)
        else -> throw IllegalArgumentException("Term must be of type String, not ${term::class}")
    }
}

fun resolveToKeywordMapTerm(term: Term, computer: Computer): Term.Value.Container.KeywordMap {
    return when (term) {
        is Term.Value.Container.KeywordMap -> term
        is Term.FunctionApplication -> resolveToKeywordMapTerm(computer.evaluate(term).get(), computer)
        else -> throw IllegalArgumentException("Term must be of type KeywordMap, not ${term::class}")
    }
}

open class SubstitutingResolver(private val matcher: (Term.FunctionApplication) -> Boolean, private val transform: (Term.FunctionApplication) -> Term.FunctionApplication) : TermResolver<Term.FunctionApplication> {

    override fun canResolve(term: Term) = term is Term.FunctionApplication && matcher(term)

    override fun resolve(term: Term, computer: Computer) = transform(term as Term.FunctionApplication)
}

class HttpResolver(private val client: Client) : TermResolver<Term.Value.Atom.String> {
    override fun canResolve(term: Term) = term is Term.FunctionApplication &&
        term.symbol == httpFn && urlTerm(term) != Term.Value.Atom.Nil

    override fun resolve(term: Term, computer: Computer): Term.Value.Atom.String {
        val urlTerm = urlTerm(term as Term.FunctionApplication)
        val url = resolveToStringTerm(urlTerm, computer)
        return client.get(url)
    }

    private fun urlTerm(term: Term.FunctionApplication) = term.args.value[urlParameter]!!

    companion object {
        private val namespace = "sys.net.http"
        val urlParameter = Term.Value.Atom.Keyword(Keyword.newKeyword(namespace, "url"))
        val httpFn = Term.Value.Atom.Symbol(Symbol.newSymbol(namespace, "get")!!)
        fun forUrl(url: Term) = Term.FunctionApplication(httpFn, Term.Value.Container.KeywordMap(mapOf(urlParameter to url)))
    }

    interface Client {
        fun get(url: Term.Value.Atom.String): Term.Value.Atom.String
    }
}

class GroovyScriptResolver(private val evaluator: Evaluator) : TermResolver<Term> {

    override fun canResolve(term: Term) = term is Term.FunctionApplication &&
        term.symbol == groovyFn && sourceTerm(term) != Term.Value.Atom.Nil && argsTerm(term) != Term.Value.Atom.Nil

    override fun resolve(term: Term, computer: Computer): Term {
        val sourceArg = resolveToStringTerm(sourceTerm(term as Term.FunctionApplication), computer)
        val argsMap = resolveToKeywordMapTerm(argsTerm(term), computer)
        return evaluator.evaluate(term.symbol, sourceArg, argsMap, computer)
    }

    private fun sourceTerm(term: Term.FunctionApplication) = term.args.value[sourceParameter]!!

    private fun argsTerm(term: Term.FunctionApplication) = term.args.value[argsParameter]!!

    companion object {
        private val namespace = "sys.scripting.groovy"
        val sourceParameter = Term.Value.Atom.Keyword(Keyword.newKeyword(namespace, "source"))
        private val argsParameter = Term.Value.Atom.Keyword(Keyword.newKeyword(namespace, "args"))
        val groovyFn = Term.Value.Atom.Symbol(Symbol.newSymbol("sys.scripting.groovy", "eval")!!)
        fun forSourceAndArgs(source: Term, args: Term) = Term.FunctionApplication(groovyFn, Term.Value.Container.KeywordMap(mapOf(sourceParameter to source, argsParameter to args)))
    }

    interface Evaluator {
        fun evaluate(symbol: Term.Value.Atom.Symbol, source: Term.Value.Atom.String, args: Term.Value.Container.KeywordMap, computer: Computer): Term
    }
}

class GroovyUriScriptResolver(symbol: Term.Value.Atom.Symbol, url: Term): SubstitutingResolver(
    { (fn) -> fn == symbol },
    { fnApply -> GroovyScriptResolver.forSourceAndArgs(HttpResolver.forUrl(url), fnApply.args) }
)

@Suppress("unused")
class FunctionRenameResolver(from: Term.Value.Atom.Symbol, to: Term.Value.Atom.Symbol): SubstitutingResolver(
    { (fn) -> fn == from },
    { fnApply -> Term.FunctionApplication(to, Term.Value.Container.KeywordMap(fnApply.args.value.mapKeys { Term.Value.Atom.Keyword(Keyword.newKeyword(to.value.prefix, it.key.value.name))  })) }
)