package io.kutoa

abstract class Function(val identifier: TSymbol) {
    // TODO("Allow functions to return lambdas")
    abstract fun apply(term: TApplication, bindings: Bindings, computer: Computer) : Evaluation
}

object ListFunction : Function(TSymbol("sys", "list")) {
    override fun apply(term: TApplication, bindings: Bindings, computer: Computer)
        = Evaluation.applyFunction(term, bindings, TList(term.args))

}

object GetFunction : Function(TSymbol("sys", "get")) {
    override fun apply(term: TApplication, bindings: Bindings, computer: Computer): Evaluation {
        if (term.args.size != 2) throw SyntaxError("$identifier requires two arguments")
        val (listTerm, indexTerm) = term.args
        val list = (listTerm as? TList)?.value
            ?: throw SyntaxError("First argument to $identifier must be a List")
        val index = (indexTerm as? TInteger)?.value?.toInt()
            ?: throw SyntaxError("Second argument to $identifier must be an Integer")
        val result = list.run { if (size < index + 1) TNil else get(index) }
        return Evaluation.applyFunction(term, bindings, result)
    }
}

object LetFunction : Function(TSymbol("sys", "let")) {
    override fun apply(term: TApplication, bindings: Bindings, computer: Computer): Evaluation {
        if (term.args.size != 2) throw SyntaxError("$identifier requires two arguments")
        val (bindingsListTerm, termToEvaluate) = term.args
        val letBindings = (bindingsListTerm as? TList)?.value
            ?.map { (it as? TList)?.value?.apply { if (size == 2) throw syntaxError } ?: throw syntaxError }
            ?.associate { Pair(it[0] as? TSymbol ?: throw syntaxError, it[1]) }
            ?:mutableMapOf()
        return computer.evaluate(termToEvaluate, bindings + letBindings)
    }
    private val syntaxError = SyntaxError("$identifier bindings must be Lists of [Symbol Term] pairs")
}

object StrConcatFunction : Function(TSymbol("sys.string", "concat")) {
    override fun apply(term: TApplication, bindings: Bindings, computer: Computer): Evaluation {
        val result = term.args.joinToString(separator = "", transform = this::stringify).toTerm()
        return Evaluation.applyFunction(term, bindings, result, emptyList())
    }
    private fun stringify(term: Term) : String =
        when (term) {
            is Term.Atom.Constant<Any> -> term.value.toString()
            else -> throw SyntaxError("Cannot coerce non-atomic Term '$term' into a String")
        }
}