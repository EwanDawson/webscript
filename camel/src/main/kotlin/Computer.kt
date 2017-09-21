import java.util.*

/**
 * @author Ewan
 */

class Computer(invokers: List<FunctionInvoker>, private val cache: Cache) {

    val id = UUID.randomUUID()!!

    val operators = listOf(CacheRetriever(cache), SymbolSubstituter) + invokers

    fun evaluate(term: Term.FunctionApplication, context: Context): FunctionEvaluation {
        var currentTerm: Term = term
        var currentContext = context
        val operations = mutableListOf<Operation<*,*>>()
        while (currentTerm !is Term.Value<*>) {
            val operator = operators.find { it.matches(currentTerm, currentContext) } ?: throw UnresolvableTermException(currentTerm, currentContext)
            val operation = operator.operate(currentTerm, currentContext)
            if (operation !is CachedOperation) cache.put(Cache.Key(currentTerm, currentContext), operation)
            operations.add(operation)
            currentTerm = operation.outputTerm
            currentContext = operation.outputContext
        }
        val result = FunctionEvaluation(term, currentTerm, context, currentContext, operations.toList())
        cache.put(Cache.Key(term, context), result)
        return result
    }
}

data class Context(val substitutions: List<Substitution>)

data class Substitution(val from: Term.Value.Atom.Symbol, val to: Term)

sealed class Operation<out From:Term, out To:Term>(
    val type: String,
    open val inputTerm: From,
    open val outputTerm: To,
    open val inputContext: Context,
    open val outputContext: Context,
    open val subOps: List<Operation<*,*>>
)

data class FunctionResolution(
    override val inputTerm: Term.FunctionApplication,
    override val outputTerm: Term.FunctionApplication,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term.FunctionApplication, Term.FunctionApplication>("FNRESL", inputTerm, outputTerm, inputContext, outputContext, subOps)

data class SymbolSubstitution(
    override val inputTerm: Term.Value.Atom.Symbol,
    override val outputTerm: Term,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term.Value.Atom.Symbol, Term>("SYMSUB", inputTerm, outputTerm, inputContext, outputContext, subOps)

data class FunctionInvocation(
    val invoker: String,
    override val inputTerm: Term.FunctionApplication,
    override val outputTerm: Term,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term.FunctionApplication, Term>("FNRSLN", inputTerm, outputTerm, inputContext, outputContext, subOps)

data class FunctionEvaluation(
    override val inputTerm: Term.FunctionApplication,
    override val outputTerm: Term.Value<*>,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term.FunctionApplication, Term.Value<*>>("FNEVAL", inputTerm, outputTerm, inputContext, outputContext, subOps)

data class NoOperation(
    override val inputTerm: Term,
    override val outputTerm: Term,
    override val inputContext: Context,
    override val outputContext: Context,
    override val subOps: List<Operation<*,*>>
) : Operation<Term, Term>("NOOPER", inputTerm, outputTerm, inputContext, outputContext, subOps) {
    constructor(term: Term, context: Context) : this(term, term, context, context, emptyList())
}

data class CachedOperation(
    val operation: Operation<*,*>
) : Operation<Term, Term>("CACHED", operation.inputTerm, operation.outputTerm, operation.inputContext, operation.outputContext, listOf(operation))

interface Operator<out From:Term, out To:Term> {
    fun matches(term: Term, context: Context): Boolean
    fun operate(term: Term, context: Context): Operation<From, To>
}

class CacheRetriever(private val cache: Cache) : Operator<Term, Term> {
    override fun matches(term: Term, context: Context): Boolean {
        return cache.exists(Cache.Key(term, context))
    }

    override fun operate(term: Term, context: Context): CachedOperation {
        return cache.get(Cache.Key(term, context))
    }
}

object SymbolSubstituter : Operator<Term.Value.Atom.Symbol, Term> {
    override fun matches(term: Term, context: Context): Boolean {
        return term is Term.Value.Atom.Symbol && context.substitutions.any { it.from == term }
    }

    override fun operate(term: Term, context: Context): SymbolSubstitution {
        term as Term.Value.Atom.Symbol
        val substitution = context.substitutions.find { it.from == term }!!.to
        return SymbolSubstitution(term, substitution, context, context, emptyList())
    }
}

object FunctionResolver : Operator<Term.FunctionApplication, Term.FunctionApplication> {
    override fun matches(term: Term, context: Context): Boolean {
        return term is Term.FunctionApplication && SymbolSubstituter.matches(term.symbol, context)
    }

    override fun operate(term: Term, context: Context): FunctionResolution {
        term as Term.FunctionApplication
        val steps = mutableListOf<SymbolSubstitution>()
        var symbol = term.symbol
        while (SymbolSubstituter.matches(symbol, context)) {
            val substitution = SymbolSubstituter.operate(symbol, context)
            steps.add(substitution)
            symbol = (substitution.outputTerm as Term.FunctionApplication).symbol
        }
        return FunctionResolution(term, steps.last().outputTerm as Term.FunctionApplication, context, context, steps.toList())
    }

}

interface FunctionInvoker : Operator<Term.FunctionApplication, Term>

@Suppress("unused")
class UnresolvableTermException(val term: Term, val context: Context) : RuntimeException("No resolver found for (term,context)")