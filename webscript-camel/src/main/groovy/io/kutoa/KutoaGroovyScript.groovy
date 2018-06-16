package io.kutoa

import static io.kutoa.TermKt.term

abstract class KutoaGroovyScript extends Script {
    def methodMissing(String name, def args) {
        final computer = binding.variables["__computer"] as Computer
        final bindings = binding.variables["__bindings"] as Map<Term.Atom.Symbol, ? extends Term>
        final substeps = binding.variables["__substeps"] as List<Evaluation>
        final symbol = new Term.Atom.Symbol(name)
        final argTerms = (args as Object[]).collect { term(it) }
        final term = new Term.Application(symbol, argTerms)
        final evaluation = computer.evaluate(term, bindings)
        substeps << evaluation
        final result = evaluation.result
        if (result instanceof Term.Atom.Constant.Error) throw new RuntimeException(result.unwrap().toString())
        return evaluation.result.unwrap()
    }
}
