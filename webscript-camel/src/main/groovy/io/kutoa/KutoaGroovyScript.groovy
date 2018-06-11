package io.kutoa

abstract class KutoaGroovyScript extends Script {
    def methodMissing(String name, def args) {
        final computer = binding.variables["__computer"] as Computer
        final bindings = binding.variables["__bindings"] as Map<Term.Atom.Symbol, ? extends Term>
        final substeps = binding.variables["__substeps"] as java.util.List<Evaluation>
        final symbol = new Term.Atom.Symbol(name)
        final argTerms = (args as Object[]).collect { MainKt.term(it) }
        final term = new Term.Application(symbol, argTerms)
        final evaluation = computer.evaluate(term, bindings)
        substeps << evaluation
        final result = evaluation.result
        if (result instanceof Term.Atom.Error) throw new RuntimeException(result.unwrap().toString())
        return evaluation.result.unwrap()
    }
}
