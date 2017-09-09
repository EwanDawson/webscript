import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.time.Instant

/**
 * @author Ewan
 */

class Context(val resolvers: List<TermResolver>, val args: Map<Keyword, Argument> = mapOf()) {
    fun resolve(term: Any?) : Any? {
        return when (term) {
            null, is String, is Long, is BigInteger, is Double,
            is BigDecimal, is Char, is Boolean, is Instant, is URI,
            is Symbol, is Keyword -> term
            is Set<*> -> setOf(term.map { resolve(it) })
            is List<*> -> listOf(term.map { resolve(it) })
            is Map<*, *> -> term.map { Pair(resolve(it.key), resolve(it.value)) }.toMap()
            else -> {
                val resolvedTerm = resolvers.find { it.canResolve(term) }?.resolve(term, this)
                    ?: throw UnresolvableTermException(term)
                return resolve(resolvedTerm)
            }
        }
    }
    fun withResolver(resolver: TermResolver) = Context(listOf(resolver) + resolvers, args)
    fun withSubstitution(from: Fn, to: Fn) = withResolver(SubstitutingResolver(from, to))
    fun withArgs(newArgs: Map<Keyword, Any?>) = Context(resolvers, newArgs.mapValues { Argument(it.key, it.value, this) })
    companion object {
        val default = Context(listOf(HttpResolver(), GroovyScriptResolver()))
    }
}

data class Argument internal constructor(val name: Keyword, val term: Any?, private val context: Context) {
    val value: Any? by lazy {
        context.resolve(term)
    }
}

class UnresolvableTermException(term: Any?) : Throwable("No resolver found for term '$term'")
