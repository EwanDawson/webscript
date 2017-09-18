import org.slf4j.LoggerFactory.getLogger
import us.bpsm.edn.Symbol
import java.io.PrintStream
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.function.Supplier

/**
 * @author Ewan
 */

val defaultExecutorService: ExecutorService = Executors.newCachedThreadPool()

class Computer(val context: Context, private val executorService: ExecutorService = defaultExecutorService) {
    val id = UUID.randomUUID().toString()

    private val log = getLogger("Computer_${id.takeLast(6)}")
    private var state = State.UNSTARTED

    private var computedTerm: Term.Value<*>? = null
    val result: Term.Value<*>
        get() = ifCompletedOrError { computedTerm!! }

    private val childrenInProgress: MutableList<Computer> = mutableListOf()
    val children: List<Computer>
        get() = ifCompletedOrError { childrenInProgress.toList() }

    private fun <T> ifCompletedOrError(supplier: () -> T): T {
        if (state == State.COMPLETED) return supplier.invoke()
        else throw IllegalStateException("Computation has not completed")
    }

    fun evaluate(): Future<Term.Value<*>> {
        if (state == State.UNSTARTED) {
            state = State.RUNNING
            try {
                val term = context.term
                return when (term) {
                    is Term.Value<*> -> {
                        computedTerm = term
                        CompletableFuture.completedFuture(term)
                    }
                    is Term.FunctionApplication -> {
                        log.info("Evaluating: $term")
                        val resolver = context.resolvers.find { it.canResolve(term) }
                        if (resolver != null) return supplyAsync(Supplier<Term.Value<*>> {
                            val resolvedTerm = resolver.resolve(term, this)
                            computedTerm = when (resolvedTerm) {
                                is Term.FunctionApplication -> evaluate(resolvedTerm).get()
                                is Term.Value<*> -> resolvedTerm
                            }
                            log.info("Result: $computedTerm")
                            computedTerm!!
                        }, executorService)
                        else throw UnresolvableTermException(term)
                    }
                }
            } finally {
                state = State.COMPLETED
            }
        }
        else throw IllegalStateException("Computation has already been run")
    }

    fun evaluate(term: Term.FunctionApplication) : Future<Term.Value<*>> {
        val childComputation = Computer(context.copy(term = term), executorService)
        childrenInProgress.add(childComputation)
        return childComputation.evaluate()
    }

    fun printEvaluationTree(writer: PrintStream, indent: Int = 0) {
        if (state != State.COMPLETED) throw IllegalStateException("Computer must complete before evaluation tree can be printed")
        fun write(value: CharSequence) = writer.appendln(" ".repeat(indent) + value)
        write("Computer $id")
        write("Term: ${context.term.toEDN()}")
        write("Result: ${result.toEDN()}")
        if (children.isNotEmpty()) {
            write("Child computations:")
            children.forEach { it.printEvaluationTree(writer, indent + 4) }
        }
    }

    override fun toString(): String {
        return "Computer(id='$id', state=$state, context=$context)"
    }

    private enum class State { UNSTARTED, RUNNING, COMPLETED }


}

data class Context constructor(val term: Term,
                               val resolvers: List<TermResolver<*>> = listOf(
                                   HttpResolver(CamelHttpClient),
                                   GroovyScriptResolver(GroovyEvaluator)
                               )) {

    val id = UUID.randomUUID().toString()

    fun withResolver(resolver: TermResolver<*>) = copy(resolvers = listOf(resolver) + resolvers)
}

class UnresolvableTermException(term: Any?) : Throwable("No resolver found for term '$term'")

fun main(args: Array<String>) {
    val function = Term.Value.Atom.Symbol(Symbol.newSymbol("two-plus-two"))
    val url = Term.Value.Atom.String("https://gist.githubusercontent.com/EwanDawson/8f069245a235be93e3b4836b4f4fae61/raw/1ba6e295c8a6b10d1f325a952ddf4a3546bd0415/two-plus-two.groovy")
    val context = Context(Term.parse("(two-plus-two)")).withResolver(GroovyUriScriptResolver(function, url))
    val computer = Computer(context)
    println(computer.evaluate().get().value)
    computer.printEvaluationTree(System.out)
    defaultExecutorService.shutdown()
}