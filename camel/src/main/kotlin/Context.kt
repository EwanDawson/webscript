/**
 * @author Ewan
 */

class Context(val resolvers: List<TermResolver>, val args: Map<String, Argument> = mapOf()) {
    fun resolve(term: Term) : Value<*> {
        return when (term) {
            is Value<*> -> term
            else -> {
                val resolvedTerm = resolvers.find { it.canResolve(term) }?.resolve(term, this)
                    ?: throw UnresolvableTermException(term)
                return resolve(resolvedTerm)
            }
        }
    }
    fun withResolver(resolver: TermResolver) = Context(listOf(resolver) + resolvers, args)
    fun withSubstitution(from: Fn, to: Fn) = withResolver(SubstitutingResolver(from, to))
    fun withArgs(newArgs: Map<String, Term>) = Context(resolvers, newArgs.mapValues { Argument(it.key, it.value, this) })
    companion object {
        val default = Context(listOf(HttpResolver(), GroovyScriptResolver()))
    }
}

data class Argument internal constructor(val name: String, val term: Term, private val context: Context) {
    val value: Any? by lazy {
        context.resolve(term).value
    }
}

class UnresolvableTermException(term: Term) : Throwable("No resolver found for term '$term'")
