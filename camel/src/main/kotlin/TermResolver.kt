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
    override fun canResolve(term: Any?) = term is URI && (term.scheme == "http" || term.scheme == "https")

    override fun resolve(term: Any?, context: Context): String {
        if (term !is URI) throw IllegalArgumentException()
        val url = term.toURL().toString().replaceFirst("://", "4://")
        return (Camel.producer.requestBody(url, null, String::class.java))
    }
}

class GroovyScriptResolver : TermResolver {
    override fun canResolve(term: Any?) = term is FnScript && term.lang == "groovy"

    override fun resolve(term: Any?, context: Context): Any? {
        val source = when (term) {
            is FnInlineScript -> term.source.toString()
            is FnURIScript -> context.resolve(term.sourceUri) as? String ?: throw IllegalArgumentException()
            else -> throw IllegalArgumentException()
        }
        return Groovy.evaluate(source, context)
    }
}