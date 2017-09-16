import org.slf4j.LoggerFactory.getLogger
import us.bpsm.edn.Symbol
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * @author Ewan
 */

class Context private constructor(private val term: Term,
              private val resolvers: List<TermResolver<*>>,
              private val executor: ExecutorService): Callable<Term> {

    val id = UUID.randomUUID().toString()

    private val log = getLogger("Context_$id")

    override fun call(): Term {
        val resolved = when (term) {
            is Term.Atom<*>, is Term.Container<*> -> term
            is Term.FunctionApplication -> resolvers.find { it.canResolve(term) }?.resolve(term, this) ?: throw UnresolvableTermException(term)
        }
        if (term != resolved) log.info("$term -> $resolved")
        return resolved
    }

    fun withResolver(resolver: TermResolver<*>) = Context(term, listOf(resolver) + resolvers, executor)

    private fun withTerm(term: Term) = Context(term, resolvers, executor)

    fun resolve(term: Term) = executor.submit(withTerm(term))!!

    fun reify(term: Term): Future<Any?> {
        return when (term) {
            is Term.Atom<*> -> completedFuture(term.value)
            is Term.Container.Set -> completedFuture(term.value.map { reify(it).get() }.toSet())
            is Term.Container.List -> completedFuture(term.value.map { reify(it).get() })
            is Term.Container.Map -> completedFuture(term.value.map { Pair(reify(it.key), reify(it.value)) }.toMap())
            is Term.Container.KeywordMap -> completedFuture(term.value.map { Pair(reify(it.key), reify(it.value)) }.toMap())
            is Term.FunctionApplication -> reify(resolve(term).get())
        }
    }

    companion object {
        fun create() = Context(
            Term.Atom.Nil,
            listOf(
                HttpResolver(CamelHttpClient),
                GroovyScriptResolver(GroovyEvaluator)
            ),
            Executors.newCachedThreadPool())
    }
}

class UnresolvableTermException(term: Any?) : Throwable("No resolver found for term '$term'")

fun main(args: Array<String>) {
    val function = Term.Atom.Symbol(Symbol.newSymbol("hello"))
    val url = Term.Atom.String("https://gist.githubusercontent.com/EwanDawson/8f069245a235be93e3b4836b4f4fae61/raw/1ba6e295c8a6b10d1f325a952ddf4a3546bd0415/two-plus-two.groovy")
    val context = Context.create().withResolver(GroovyUriScriptResolver(function, url))
    println(context.reify(Term.parse("(hello)")).get())
}