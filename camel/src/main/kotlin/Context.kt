import org.slf4j.LoggerFactory.getLogger
import us.bpsm.edn.Symbol

/**
 * @author Ewan
 */

class Context(private val resolvers: List<TermResolver<*>>) {
    fun resolve(term: Term) : Term {
        val resolved = when (term) {
            is Term.Atom<*>, is Term.Container<*> -> term
            is Term.FunctionApplication -> resolvers.find { it.canResolve(term) }?.resolve(term, this) ?: throw UnresolvableTermException(term)
        }
        if (term != resolved) log.info("$term -> $resolved")
        return resolved
    }
    fun withResolver(resolver: TermResolver<*>) = Context(listOf(resolver) + resolvers)
    companion object {
        val default = Context(listOf(
            HttpResolver(CamelHttpClient),
            GroovyScriptResolver(GroovyEvaluator)
        ))
        val log = getLogger("Context")!!
    }
}

class UnresolvableTermException(term: Any?) : Throwable("No resolver found for term '$term'")

fun main(args: Array<String>) {
    val function = Term.Atom.Symbol(Symbol.newSymbol("hello"))
    val url = Term.Atom.String("https://gist.githubusercontent.com/EwanDawson/8f069245a235be93e3b4836b4f4fae61/raw/1ba6e295c8a6b10d1f325a952ddf4a3546bd0415/two-plus-two.groovy")
    val context = Context.default
        .withResolver(GroovyUriScriptResolver(function, url))
    var term = Term.parse("(hello)")
    while (term is Term.FunctionApplication) term = context.resolve(term)
    println(term)
}