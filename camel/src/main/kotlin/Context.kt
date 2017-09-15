/**
 * @author Ewan
 */

class Context(private val resolvers: List<TermResolver<*>>) {
    fun resolve(term: Term) : Term {
        return when (term) {
            Term.Atom.Nil, is Term.Value<*> -> term
            is Term.Container.Set -> Term.Container.Set(term.value.map { resolve(it) }.toSet())
            is Term.Container.List -> Term.Container.List(term.value.map { resolve(it) })
            is Term.Container.Map -> Term.Container.Map(term.value.map { Pair(resolve(it.key), resolve(it.value)) }.toMap())
            else -> {
                val resolvedTerm = resolvers.find { it.canResolve(term) }?.resolve(term, this)
                    ?: throw UnresolvableTermException(term)
                return resolve(resolvedTerm)
            }
        }
    }
    fun withResolver(resolver: TermResolver<*>) = Context(listOf(resolver) + resolvers)
    fun withSubstitution(from: Term.Atom.Function, to: Term.Atom.Function) = withResolver(SubstitutingResolver(from, to))
    companion object {
        val default = Context(listOf(HttpResolver(CamelHttpClient), GroovyScriptResolver(GroovyEvaluator)))
    }
}

class UnresolvableTermException(term: Any?) : Throwable("No resolver found for term '$term'")
