package io.kutoa

data class Evaluation(val input: Term, val bindings: Bindings, val result: Term, val operation: Operation,
                      val subSteps: kotlin.collections.List<Evaluation>, val dependencies: Bindings) {

    companion object {
        fun constant(input: Term, bindings: Bindings) : Evaluation {
            assert(input.isConstant)
            return Evaluation(input, bindings, input, Operation.CONSTANT, emptyList(), emptyMap())
        }
        fun constant(input: Term) = constant(input, emptyMap())

        fun bindSymbol(input: TSymbol, bindings: Bindings, result: Term, substep: Evaluation) : Evaluation {
            return Evaluation(input, bindings, result, Operation.BIND_SYMBOL, listOf(substep),
                                       mapOf(input to bindings[input]!!))
        }

        fun bindSymbol(input: TSymbol, bindings: Bindings, throwable: Throwable) : Evaluation {
            return Evaluation(input, bindings, Term.Atom.Constant.Error(throwable),
                                       Operation.BIND_SYMBOL, emptyList(), emptyMap())
        }

        fun compound(term: TCompound<*>, bindings: Bindings, result: Term,
                     subSteps: kotlin.collections.List<Evaluation>): Evaluation {
            return Evaluation(term, bindings, result, Operation.COMPOUND, subSteps, emptyMap())
        }

        fun cacheHit(term: Term, bindings: Bindings, cachedResult: Term): Evaluation {
            return Evaluation(term, bindings, cachedResult, Operation.CACHE_HIT, emptyList(), emptyMap())
        }

        fun applyFunction(term: TApplication, bindings: Bindings, result: Term, subInvocations: kotlin.collections.List<Evaluation> = emptyList()) =
            Evaluation(term, bindings, result, Operation.APPLY_FUNCTION, subInvocations, emptyMap())

        fun macroExpansion(term: TApplication, bindings: Bindings, expansion: TApplication) =
            Evaluation(term, bindings, expansion, Operation.EXPAND_MACRO, emptyList(),
                                mapOf(term.symbol to bindings[term.symbol]!!))
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