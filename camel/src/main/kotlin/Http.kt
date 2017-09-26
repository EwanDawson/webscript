import us.bpsm.edn.Symbol
import java.util.concurrent.CompletableFuture

/**
 * @author Ewan
 */

class HttpInvoker(private val client: Client, private val computer: Computer) : FunctionInvoker {
    override fun matches(term: Term, context: Context): Boolean {
        return term is Term.FunctionApplication && term.symbol == httpFn && term.args[0] != Term.Value.Atom.Nil
    }

    override suspend fun operate(term: Term, context: Context): FunctionInvocation {
        term as Term.FunctionApplication
        val steps = mutableListOf<Operation<*,*>>()
        val urlTerm = term.args[0]
        val url = when (urlTerm) {
            is Term.Value<*> -> urlTerm
            is Term.FunctionApplication -> {
                val evaluation = computer.evaluate(urlTerm, context)
                steps.add(evaluation)
                evaluation.outputTerm
            }
        } as? Term.Value.Atom.String ?: throw IllegalArgumentException("URL must be of type String")
        val result = Term.of(client.get(url.value).get())
        return FunctionInvocation(namespace, term, result, context, context, emptyList())
    }

    companion object {
        private val namespace = "sys.net.http"
        val httpFn = Term.Value.Atom.Symbol(Symbol.newSymbol(namespace, "get")!!)
    }

    interface Client {
        fun get(url: String): CompletableFuture<String>
    }
}
