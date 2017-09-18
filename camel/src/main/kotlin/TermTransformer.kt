import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol

/**
 * @author Ewan
 */

interface TermTransformer {
    fun canTransform(term: Term): Boolean
    val type: String
}

interface Substituter: TermTransformer {
    fun substitute(term: Term.FunctionApplication): Term
    override val type get() = "Substitution"
}

interface FunctionApplicator: TermTransformer {
    val requiredArgs: List<Term.Value.Atom.Keyword>
    fun apply(symbol: Term.Value.Atom.Symbol, args: Map<Term.Value.Atom.Keyword, Data>, computer: Computer): Term
    override val type get() = "FunctionApplication"
}

open class GeneralSubstituter(private val matcher: (Term.FunctionApplication) -> Boolean, private val transform: (Term.FunctionApplication) -> Term.FunctionApplication) : Substituter {

    override fun canTransform(term: Term) = term is Term.FunctionApplication && matcher(term)

    override fun substitute(term: Term.FunctionApplication) = transform.invoke(term)
}

class HttpResolver(private val client: Client) : FunctionApplicator {
    override val requiredArgs by lazy { listOf(urlParameter) }

    override fun canTransform(term: Term) = term is Term.FunctionApplication &&
        term.symbol == httpFn && urlTerm(term) != Term.Value.Atom.Nil

    override fun apply(symbol: Term.Value.Atom.Symbol, args: Map<Term.Value.Atom.Keyword, Data>, computer: Computer): Term.Value.Atom.String {
        return Term.Value.Atom.String(client.get(args[urlParameter]!!.value as String))
    }

    private fun urlTerm(term: Term.FunctionApplication) = term.args.value[urlParameter]!!

    companion object {
        private val namespace = "sys.net.http"
        val urlParameter = Term.Value.Atom.Keyword(Keyword.newKeyword(namespace, "url"))
        val httpFn = Term.Value.Atom.Symbol(Symbol.newSymbol(namespace, "get")!!)
        fun forUrl(url: Term) = Term.FunctionApplication(httpFn, Term.Value.Container.KeywordMap(mapOf(urlParameter to url)))
    }

    interface Client {
        fun get(url: String): String
    }
}

class GroovyScriptResolver(private val evaluator: Evaluator) : FunctionApplicator {
    override val requiredArgs by lazy { listOf(sourceParameter, argsParameter) }

    override fun canTransform(term: Term) = term is Term.FunctionApplication &&
        term.symbol == groovyFn && sourceTerm(term) != Term.Value.Atom.Nil && argsTerm(term) != Term.Value.Atom.Nil

    override fun apply(symbol: Term.Value.Atom.Symbol, args: Map<Term.Value.Atom.Keyword, Data>, computer: Computer): Term {
        val source = args[sourceParameter]!!.value as String
        val scriptArgs =  (args[argsParameter]!!.value as Map<*, *>).map { Pair((it.key as Data).value.toString(), it.value as Data) }.toMap()
        return evaluator.evaluate(symbol, source, scriptArgs, computer)
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
        fun evaluate(symbol: Term.Value.Atom.Symbol, source: String, args: Map<String, Data>, computer: Computer): Term
    }
}

class FunctionToGroovyUriScriptSubstituter(symbol: Term.Value.Atom.Symbol, url: Term): GeneralSubstituter(
    { (fn) -> fn == symbol },
    { fnApply -> GroovyScriptResolver.forSourceAndArgs(HttpResolver.forUrl(url), fnApply.args) }
)

@Suppress("unused")
class FunctionRenamer(from: Term.Value.Atom.Symbol, to: Term.Value.Atom.Symbol): GeneralSubstituter(
    { (fn) -> fn == from },
    { fnApply -> Term.FunctionApplication(to, Term.Value.Container.KeywordMap(fnApply.args.value.mapKeys { Term.Value.Atom.Keyword(Keyword.newKeyword(to.value.prefix, it.key.value.name))  })) }
)