import org.slf4j.LoggerFactory.getLogger
import java.io.PrintStream
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

/**
 * @author Ewan
 */

val defaultExecutorService: ExecutorService = Executors.newCachedThreadPool()

class Computer(val context: Context, private val executorService: ExecutorService = defaultExecutorService) {
    val id = UUID.randomUUID().toString()

    private val log = getLogger("Computer_${id.takeLast(6)}")

    private var _state = AtomicReference<State>(State.UNSTARTED)
    val state get() = _state.get()!!

    private var _result: Term.Value<*> = Term.Value.Atom.Nil
    val result get() = _result

    private val _children = AtomicReference<List<Computer>>(listOf())
    val children get() = _children.get()!!

    private val _steps = AtomicReference<List<Step>>(listOf())
    val steps get() = _steps.get()!!

    fun evaluate(): CompletableFuture<Computer> {
        if (_state.compareAndSet(State.UNSTARTED, State.RUNNING)) {
            return supplyAsync(Supplier<Computer>{
                var term = context.term
                while (term !is Term.Value<*>) {
                    var newTerm = term
                    val operation = term as Term.FunctionApplication
                    log.info("Evaluating: $operation")
                    val resolver = context.transformers.find { it.canTransform(operation) }
                    when (resolver) {
                        null -> throw UnresolvableTermException(operation)
                        is FunctionApplicator -> {
                            val args = operation.args.value
                                .filter { it.key in resolver.requiredArgs }
                                .mapValues { Data(it.value, this) }
                            newTerm = resolver.apply(operation.symbol, args, this)
                        }
                        is Substituter -> newTerm = resolver.substitute(term)
                    }
                    _steps.updateAndGet { it + listOf(Step(this, resolver!!, term, newTerm))  }
                    term = newTerm
                }
                _result = term
                _state.set(State.COMPLETED)
                return@Supplier this
            }, executorService)
        }
        else throw IllegalStateException("Computation has already been run")
    }

    fun evaluate(term: Term.FunctionApplication) : CompletableFuture<Computer> {
        val childComputation = Computer(context.copy(term = term), executorService)
        _children.updateAndGet { it + listOf(childComputation) }
        val future = childComputation.evaluate()
        future.thenAccept { result -> _steps.updateAndGet { it + result.steps } }
        return future
    }

    fun printEvaluationTree(writer: PrintStream, indent: Int = 0) {
        fun write(value: CharSequence) = writer.appendln(" ".repeat(indent) + value)
        write("Computer $id")
        write("State $state")
        write("Term: ${context.term.toEDN()}")
        steps.forEachIndexed { index, step ->
            write("Step $index: $step")
        }
        write("Result: ${result.toEDN()}")
        if (children.isNotEmpty()) {
            write("Child computations:")
            children.forEach { it.printEvaluationTree(writer, indent + 4) }
        }
    }

    override fun toString(): String {
        return "Computer(id='$id', state=$_state, context=$context)"
    }

    enum class State { UNSTARTED, RUNNING, COMPLETED }


}

data class Step internal constructor(val computer: String, val type: String, val from: String, val to: String) {
    constructor(computer: Computer, transformer: TermTransformer, from: Term, to: Term)
        : this(computer.id.takeLast(6), transformer.type, from.toEDN(), to.toEDN())
}

data class Context constructor(val term: Term,
                               val transformers: List<TermTransformer> = listOf(
                                   HttpResolver(CamelHttpClient),
                                   GroovyScriptResolver(GroovyEvaluator)
                               )) {

    val id = UUID.randomUUID().toString()

    fun withResolver(transformer: TermTransformer) = copy(transformers = listOf(transformer) + transformers)
}

class UnresolvableTermException(term: Any?) : Throwable("No resolver found for term '$term'")

fun main(args: Array<String>) {
    val context = Context(Term.parse("(async-test{:a 1 :b 2})"))
        .withResolver(FunctionToGroovyUriScriptSubstituter(
            Term.symbol("two-plus-two"),
            Term.string("https://gist.githubusercontent.com/EwanDawson/8f069245a235be93e3b4836b4f4fae61/raw/1ba6e295c8a6b10d1f325a952ddf4a3546bd0415/two-plus-two.groovy")
        ))
        .withResolver(FunctionToGroovyUriScriptSubstituter(
            Term.symbol("add"),
            Term.string("https://gist.githubusercontent.com/EwanDawson/a5aee75d7819978b27c6b73cb5815c36/raw/44a648bdd8a766b45d825911e69db4c4125477f0/add.groovy")
        ))
        .withResolver(FunctionToGroovyUriScriptSubstituter(
            Term.symbol("async-test"),
            Term.string("https://gist.githubusercontent.com/EwanDawson/d89a881fce76e0f02fca7349b9f6a925/raw/835acd6e1e7369fb7907d852260152b05e25e25f/asyncTest.groovy")
        ))
    val computer = Computer(context)
    println(computer.evaluate().get().result.value)
    computer.printEvaluationTree(System.out)
    defaultExecutorService.shutdown()
}