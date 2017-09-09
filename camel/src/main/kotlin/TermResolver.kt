/**
 * @author Ewan
 */

interface TermResolver {
    fun canResolve(term: Term): Boolean
    fun resolve(term: Term, context: Context): Term
}

class SubstitutingResolver(val identifier: Fn, val replacement: Fn) : TermResolver {

    override fun canResolve(term: Term) = term.term == identifier.term

    override fun resolve(term: Term, context: Context): Term {
        return replacement
    }
}

class HttpResolver : TermResolver {
    override fun canResolve(term: Term) = term is URLTerm

    override fun resolve(term: Term, context: Context): Term {
        val url = (term as URLTerm).url.toString().replaceFirst("://", "4://")
        return Term.of(Camel.producer.requestBody(url, null, String::class.java))
    }

}

class GroovyScriptResolver : TermResolver {
    override fun canResolve(term: Term) = term is FnScript && term.lang == "groovy"

    override fun resolve(term: Term, context: Context): Term {
        val source = (context.resolve((term as FnScript).source) as StringTerm).value
        return Term.of(Groovy.evaluate(source, context))
    }
}