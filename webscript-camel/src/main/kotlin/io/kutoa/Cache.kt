package io.kutoa

class Cache {

    private val cache = mutableMapOf<Term, MutableMap<Bindings, Term>>()

    fun getOrCompute(term: Term, bindings: Bindings, evaluator: () -> Evaluation): Evaluation {
        val cachedResult = cache[term]?.toList()?.firstOrNull {
            it.first.all { bindings.containsKey(it.key) && bindings[it.key] == it.value }
        }?.second
        return when (cachedResult) {
            null -> evaluator.invoke().let { evaluation ->
                if (!cache.containsKey(term)) cache[term] = mutableMapOf()
                cache[term]!![evaluation.dependencies] = evaluation.result
                evaluation
            }
            else -> Evaluation.cacheHit(term, bindings, cachedResult)
        }
    }

    fun clear() { cache.clear() }
}