import org.apache.http.client.utils.URLEncodedUtils
import java.nio.charset.StandardCharsets

/**
 * @author Ewan
 */

interface TermResolver {
    fun canResolve(term: Identifier): Boolean
    fun resolve(term: Identifier, context: Context): Term
}

class BindingResolver(val identifier: String, val replacement: String) : TermResolver {

    private val regex = ("^(" + Regex.escape(identifier) + ")(\\?.+)?$").toRegex(RegexOption.IGNORE_CASE)

    override fun canResolve(term: Identifier): Boolean = regex.matches(term.value)

    override fun resolve(term: Identifier, context: Context): Term {
        return Term.of(regex.replaceFirst(identifier, replacement))
    }
}

class HttpResolver : TermResolver {
    override fun canResolve(term: Identifier) = term.uri.scheme.startsWith("http", true)

    override fun resolve(term: Identifier, context: Context): Term {
        val url = term.uri.toString().replaceFirst("://", "4://")
        return Term.of(Camel.producer.requestBody(url, null, String::class.java))
    }

}

class GroovyScriptResolver : TermResolver {
    override fun canResolve(term: Identifier): Boolean = term.uri.scheme.equals("groovy", true)

    override fun resolve(term: Identifier, context: Context): Term {
        val source = resolveSource(term, context)
        val arguments = URLEncodedUtils
            .parse(term.uri, StandardCharsets.UTF_8)
            .map { Pair(it.name, Term.of(it.value)) }
            .toMap()
        val scriptEvaluationContext = context.withArgs(arguments)
        return Term.of(Groovy.evaluate(source, scriptEvaluationContext))
    }

    private fun resolveSource(term: Identifier, context: Context): String {
        val sourceTerm = Term.of(with(term.uri) { authority + path })
        return context.resolve(sourceTerm).value?.toString()
            ?: throw NullTermException("$sourceTerm resolved to a null value")
    }
}

class NullTermException(message: String) : Throwable(message)

