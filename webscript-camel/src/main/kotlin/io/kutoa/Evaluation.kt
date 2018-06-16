package io.kutoa

data class Evaluation(val input: Term, val result: Term, val operation: Operation,
                      val dependencies: Bindings,
                      val bindings: Bindings, val subSteps: List<Evaluation>) {

    companion object {
        fun constant(input: Term, bindings: Bindings) : Evaluation {
            assert(input.isConstant)
            return Evaluation(input, input, Operation.CONSTANT, emptyMap(), bindings, emptyList())
        }
        fun constant(input: Term) = constant(input, emptyMap())

        fun bindSymbol(input: TSymbol, bindings: Bindings, result: Term, substep: Evaluation) : Evaluation {
            return Evaluation(input, result, Operation.BIND_SYMBOL, mapOf(input to bindings[input]!!), bindings,
                              listOf(substep))
        }

        fun bindSymbol(input: TSymbol, bindings: Bindings, throwable: Throwable) : Evaluation {
            return Evaluation(input, Term.Atom.Constant.Error(throwable), Operation.BIND_SYMBOL,
                              emptyMap(), bindings, emptyList())
        }

        fun compound(term: TCompound<*>, bindings: Bindings, result: Term, subSteps: List<Evaluation>): Evaluation {
            return Evaluation(term, result, Operation.COMPOUND, subSteps.flatMap { it.dependencies.toList() }.toMap(), bindings, subSteps)
        }

        fun cacheHit(term: Term, bindings: Bindings, cachedResult: Term): Evaluation {
            return Evaluation(term, cachedResult, Operation.CACHE_HIT, emptyMap(), bindings, emptyList())
        }

        fun applyFunction(term: TApplication, bindings: Bindings, result: Term, subInvocations: List<Evaluation> = emptyList()) =
            Evaluation(term, result, Operation.APPLY_FUNCTION, subInvocations.flatMap { it.dependencies.toList() }.toMap(), bindings, subInvocations)

        fun macroExpansion(term: TApplication, bindings: Bindings, expansion: Term) =
            Evaluation(term, expansion, Operation.EXPAND_MACRO, mapOf(term.symbol to bindings[term.symbol]!!), bindings,
                       emptyList())
    }

    enum class Operation {
        CONSTANT,
        COMPOUND,
        BIND_SYMBOL,
        APPLY_FUNCTION,
        CACHE_HIT,
        EXPAND_MACRO
    }
}