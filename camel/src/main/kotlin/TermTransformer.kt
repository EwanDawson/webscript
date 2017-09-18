import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import java.util.concurrent.CompletableFuture

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
    fun apply(symbol: Term.Value.Atom.Symbol, args: Map<Term.Value.Atom.Keyword, Data>, computer: Computer): CompletableFuture<Term>
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

    override fun apply(symbol: Term.Value.Atom.Symbol, args: Map<Term.Value.Atom.Keyword, Data>,
                       computer: Computer): CompletableFuture<Term> {
        return client.get(args[urlParameter]!!.value.get() as String).thenApply { Term.of(it) }
    }

    private fun urlTerm(term: Term.FunctionApplication) = term.args.value[urlParameter]!!

    companion object {
        private val namespace = "sys.net.http"
        val urlParameter = Term.Value.Atom.Keyword(Keyword.newKeyword(namespace, "url"))
        val httpFn = Term.Value.Atom.Symbol(Symbol.newSymbol(namespace, "get")!!)
        fun forUrl(url: Term) = Term.FunctionApplication(httpFn, Term.Value.Container.KeywordMap(mapOf(urlParameter to url)))
    }

    interface Client {
        fun get(url: String): CompletableFuture<String>
    }
}

class GroovyScriptResolver(private val evaluator: Evaluator) : FunctionApplicator {
    override val requiredArgs by lazy { listOf(sourceParameter, argsParameter, optionsParameter) }

    override fun canTransform(term: Term) = term is Term.FunctionApplication &&
        term.symbol == groovyFn && sourceTerm(term) != Term.Value.Atom.Nil

    override fun apply(symbol: Term.Value.Atom.Symbol, args: Map<Term.Value.Atom.Keyword, Data>, computer: Computer): CompletableFuture<Term> {
        val source = args[sourceParameter]!!.value.get() as String
        val scriptArgs =  (args[argsParameter]?.value?.get() as? Map<*, *>)
            ?.map { Pair(((it.key as Data).value.get() as Keyword).name, it.value as Data) }?.toMap() ?: mapOf()
        val options = (args[optionsParameter]?.value?.get() as? Map<*, *>)
            ?.map { Pair(((it.key as Data).value.get() as Keyword).name, it.value as Data) }?.toMap() ?: mapOf()
        return evaluator.evaluate(symbol, source, scriptArgs, options, computer)
    }

    private fun sourceTerm(term: Term.FunctionApplication) = term.args.value[sourceParameter]!!

    companion object {
        private val namespace = "sys.scripting.groovy"
        val sourceParameter = Term.keyword(namespace, "source")
        private val argsParameter = Term.keyword(namespace, "args")
        val optionsParameter = Term.keyword(namespace, "options")
        val groovyFn = Term.symbol(namespace, "eval")
        fun forSourceAndArgsAndOptions(source: Term, args: Term, options: Term) =
            Term.FunctionApplication(groovyFn, Term.map(
                sourceParameter to source,
                argsParameter to args,
                optionsParameter to options
            ))
    }

    interface Evaluator {
        fun evaluate(symbol: Term.Value.Atom.Symbol, source: String, args: Map<String, Data>,
                     options: Map<String, Data>, computer: Computer): CompletableFuture<Term>
    }
}

class FunctionToGroovyUriScriptSubstituter(symbol: Term.Value.Atom.Symbol, url: Term, options: Term = Term.nil): GeneralSubstituter(
    { (fn) -> fn == symbol },
    { fnApply -> GroovyScriptResolver.forSourceAndArgsAndOptions(HttpResolver.forUrl(url), fnApply.args, options) }
)

@Suppress("unused")
class FunctionRenamer(from: Term.Value.Atom.Symbol, to: Term.Value.Atom.Symbol): GeneralSubstituter(
    { (fn) -> fn == from },
    { fnApply -> Term.FunctionApplication(to, Term.Value.Container.KeywordMap(fnApply.args.value.mapKeys { Term.Value.Atom.Keyword(Keyword.newKeyword(to.value.prefix, it.key.value.name))  })) }
)