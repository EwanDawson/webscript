package io.kutoa

class Computer(builtIns: kotlin.collections.List<Function>, private val cache: Cache) {

    private val builtInsMap = builtIns.fold(mapOf()) { map: Map<TSymbol, Function>, evaluator ->
        map + (evaluator.identifier to evaluator)
    }

    fun evaluate(term: Term, bindings: Bindings) : Evaluation =
        when {
            term.isConstant -> Evaluation.constant(term, bindings)
            else -> when (term) {
                is TConstant<*> -> Evaluation.constant(term, bindings)
                is TError -> Evaluation.constant(term, bindings)
                is TCompound<*> -> evaluateCompound(term, bindings)
                is TSymbol -> evaluateSymbol(term, bindings)
                is TApplication -> evaluateApplication(term, bindings)
            }
        }

    private fun evaluateCompound(term: TCompound<*>, bindings: Bindings) : Evaluation {
        val subSteps = mutableListOf<Evaluation>()
        return when (term) {
            is TList -> {
                term.value.forEach { subSteps.add(evaluate(it, bindings)) }
                Evaluation.compound(term, bindings,
                                    TList(subSteps.map(Evaluation::result)),
                                    subSteps)
            }
            is TSet -> {
                term.value.forEach { subSteps.add(evaluate(it, bindings)) }
                Evaluation.compound(term, bindings,
                                    TSet(subSteps.map(Evaluation::result).toSet()),
                                    subSteps)
            }
            is TMap -> {
                val result = term.map {
                    val evaluation = evaluate(it, bindings)
                    subSteps.add(evaluation)
                    evaluation.result
                }
                Evaluation.compound(term, bindings, result, subSteps)
            }
        }
    }

    private fun evaluateSymbol(term: TSymbol, bindings: Bindings) : Evaluation =
        if (bindings.containsKey(term)) {
            val binding = bindings[term]!!
            if (binding.isConstant) {
                Evaluation(term, bindings, binding, Evaluation.Operation.BIND_SYMBOL, emptyList(),
                           mapOf(term to binding))
            } else {
                val substep = evaluate(bindings[term]!!, bindings)
                Evaluation(term, bindings, substep.result, Evaluation.Operation.BIND_SYMBOL,
                           listOf(substep), mapOf(term to binding) + substep.dependencies)
            }
        }
        else {
            val error = UnknownSymbolException(term)
            Evaluation.bindSymbol(term, bindings, error)
        }

    private fun evaluateApplication(term: TApplication, bindings: Bindings) : Evaluation =
        cache.getOrCompute(term, bindings) {
            val subSteps = mutableListOf<Evaluation>()
            val dependencies = mutableMapOf<TSymbol, Term>()
            when {
                builtInsMap.containsKey(term.symbol) -> {
                    evaluateBuiltIn(term, builtInsMap[term.symbol]!!, bindings)
                }
                bindings.containsKey(term.symbol) -> {
                    val expansion = expandMacro(term, bindings[term.symbol] as TApplication)
                    subSteps.add(Evaluation.macroExpansion(term, bindings, expansion))
                    val evaluation = evaluateApplication(expansion, bindings)
                    dependencies[term.symbol] = bindings[term.symbol]!!
                    dependencies.putAll(evaluation.dependencies)
                    subSteps.add(evaluation)
                    Evaluation(term, bindings, evaluation.result, Evaluation.Operation.APPLY_FUNCTION,
                               subSteps, dependencies)
                }
                else -> {
                    val error = UnknownSymbolException(term.symbol)
                    Evaluation.bindSymbol(term.symbol, bindings, error)
                }
            }
        }

    private fun expandMacro(source: TApplication, macro: TApplication): TApplication {
        val bindings = mutableMapOf<TSymbol, Term>()
        bindings[TSymbol("%name")] = source.symbol
        source.args.forEachIndexed { index, term -> bindings[TSymbol("%$index")] = term }
        return expandTerm(macro, bindings) as TApplication
    }

    private fun expandTerm(term: Term, bindings: Map<TSymbol, Term>) : Term {
        return when (term) {
            is Term.Atom.Constant<Any> -> term
            is Term.Atom.Constant.Error -> term
            is Term.Atom.Symbol -> bindings[term] ?: term
            is Term.Compound<Any> -> term.map { t -> expandTerm(t, bindings) }
            is Term.Application -> {
                val symbolExpansion = expandTerm(term.symbol, bindings)
                val expandedSymbol = symbolExpansion as? TSymbol ?: throw SyntaxError(
                    "Error expanding function symbol '${term.symbol}' to '$symbolExpansion':  Macro expansion of function symbol must also be a function symbol")
                TApplication(expandedSymbol, term.args.map { expandTerm(it, bindings) })
            }
        }
    }

    private fun evaluateBuiltIn(term: TApplication, function: Function, bindings: Bindings) : Evaluation {
        val substeps = mutableListOf<Evaluation>()
        val dependencies = mutableMapOf<TSymbol, Term>()
        val resolvedArgs = term.args.map {
            if (it.isConstant) it
            else {
                val evaluation = evaluate(it, bindings)
                substeps.add(evaluation)
                dependencies.putAll(evaluation.dependencies)
                evaluation.result
            }
        }
        return try {
            val subEvaluation = function.apply(TApplication(term.symbol, resolvedArgs), bindings, this)
            if (resolvedArgs == term.args) subEvaluation
            else {
                substeps.add(subEvaluation)
                dependencies.putAll(subEvaluation.dependencies)
                Evaluation(term, bindings, subEvaluation.result, Evaluation.Operation.APPLY_FUNCTION,
                           substeps, dependencies)
            }
        } catch (e: Exception) {
            Evaluation.applyFunction(term, bindings, e.toTerm())
        }
    }
}

class UnknownSymbolException(symbol: TSymbol) : SyntaxError("Unknown symbol: $symbol")
open class SyntaxError(message: String) : RuntimeException(message)typealias Bindings = Map<TSymbol, Term>

data class ErrorInfo(val code: String, val message: String) {
    constructor(throwable: Throwable) : this(throwable::class.java.name, throwable.message?:"")
}