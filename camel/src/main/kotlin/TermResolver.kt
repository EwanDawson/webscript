import us.bpsm.edn.Symbol
import java.net.URI

/**
 * @author Ewan
 */

interface TermResolver {
    fun canResolve(term: Any?): Boolean
    fun resolve(term: Any?, context: Context): Any?
}

class SubstitutingResolver(val identifier: Fn, val replacement: Fn) : TermResolver {

    override fun canResolve(term: Any?) = term == identifier

    override fun resolve(term: Any?, context: Context): Fn {
        return if (term == identifier) replacement else throw IllegalArgumentException("Expected term '$identifier'")
    }
}

class HttpResolver : TermResolver {
    override fun canResolve(term: Any?) =
        term is FnApplicationPositionalArgs &&
            term.fn == httpFn &&
            term.args.size == 1

    override fun resolve(term: Any?, context: Context): String {
        val urlTerm = (term as FnApplicationPositionalArgs).args.first()
        val url = context.resolve(urlTerm).toString().replaceFirst("://", "4://")
        return (Camel.producer.requestBody(url, null, String::class.java))
    }
}

class GroovyScriptResolver : TermResolver {
    override fun canResolve(term: Any?) =
        term is FnApplicationPositionalArgs &&
            term.fn == groovyFn &&
            term.args.size == 1

    override fun resolve(term: Any?, context: Context): Any? {
        val sourceArg = (term as FnApplicationPositionalArgs).args.first()
        val source = when (sourceArg) {
            is URI -> context.resolve(FnApplicationPositionalArgs(httpFn, listOf(sourceArg.toURL()))).toString()
            is String -> sourceArg
            else -> throw IllegalArgumentException()
        }
        return Groovy.evaluate(source, context)
    }
}

val groovyFn = FnIdentifier(Symbol.newSymbol("sys.scripting.groovy", "eval")!!)
val httpFn = FnIdentifier(Symbol.newSymbol("sys.net.http", "get")!!)