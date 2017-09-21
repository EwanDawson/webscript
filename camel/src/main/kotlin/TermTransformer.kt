import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import java.util.concurrent.CompletableFuture

/**
 * @author Ewan
 */

interface TermTransformer {
    fun canTransform(context: ContextOLD): Boolean
    val type: String
}

interface Substituter: TermTransformer {
    fun substitute(term: Term.FunctionApplication): Term
    override val type get() = "Substitution"
}

open class GeneralSubstituter(private val matcher: (Term.FunctionApplication) -> Boolean, private val transform: (Term.FunctionApplication) -> Term.FunctionApplication) : Substituter {

    override fun canTransform(context: ContextOLD) = context.term is Term.FunctionApplication && matcher(context.term)

    override fun substitute(term: Term.FunctionApplication) = transform.invoke(term)
}

class CacheLookup(private val cache: Cache): FunctionApplicator {
    override fun canTransform(context: ContextOLD) = cache.exists(context)

    override val requiredArgs = emptyList<Term.Value.Atom.Keyword>()

    override fun apply(symbol: Term.Value.Atom.Symbol, args: Map<Term.Value.Atom.Keyword, Data>,
                       computer: Computer_OLD): CompletableFuture<Term> = CompletableFuture.completedFuture(cache.get(con))

}

class HttpResolver(private val client: Client) : FunctionApplicator {
    override val requiredArgs by lazy { listOf(urlParameter) }

    override fun canTransform(context: ContextOLD) = context.term.let {
        it is Term.FunctionApplication && it.symbol == httpFn && urlTerm(it) != Term.Value.Atom.Nil
    }

    override fun apply(symbol: Term.Value.Atom.Symbol, args: Map<Term.Value.Atom.Keyword, Data>,
                       computer: Computer_OLD): CompletableFuture<Term> {
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

class FunctionToGroovyUriScriptSubstituter(symbol: Term.Value.Atom.Symbol, url: Term, options: Term = Term.nil): GeneralSubstituter(
    { (fn) -> fn == symbol },
    { fnApply -> GroovyScriptInvoker.forSourceAndArgsAndOptions(HttpResolver.forUrl(url), fnApply.args, options) }
)

@Suppress("unused")
class FunctionRenamer(from: Term.Value.Atom.Symbol, to: Term.Value.Atom.Symbol): GeneralSubstituter(
    { (fn) -> fn == from },
    { fnApply -> Term.FunctionApplication(to, Term.Value.Container.KeywordMap(fnApply.args.value.mapKeys { Term.Value.Atom.Keyword(Keyword.newKeyword(to.value.prefix, it.key.value.name))  })) }
)