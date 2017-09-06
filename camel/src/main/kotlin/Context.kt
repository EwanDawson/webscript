/**
 * @author Ewan
 */

class Context(val resolvers: List<TermResolver>, val args: Map<String, Argument> = mapOf()) {
    fun resolve(term: Term) : Literal {
        return when (term) {
            is Literal -> term
            is Identifier -> {
                val resolvedTerm = resolvers.find { it.canResolve(term) }?.resolve(term, this) ?: throw UnresolvableTermException(term)
                return resolvedTerm as? Literal ?: resolve(resolvedTerm)
            }
            else -> throw AssertionError("Unexpected term type: '${term.javaClass}'")
        }
    }
    fun withResolver(resolver: TermResolver) = Context(listOf(resolver) + resolvers, args)
    fun withBinding(from: String, to: String) = withResolver(BindingResolver(from, to))
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

class UnresolvableTermException(term: Term) : Throwable("No resolver found for term '${term.value}'")
