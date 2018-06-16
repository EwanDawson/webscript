package io.kutoa

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.codehaus.groovy.control.CompilerConfiguration
import us.bpsm.edn.Keyword

class GroovyScriptFunction : Function(symbol) {
    override fun apply(term: TApplication, bindings: Bindings, computer: Computer): Evaluation {
        checkSyntax(term.args)
        val source = extractSource(term.args)
        val scriptArgs = extractScriptArgs(term.args)
        val subInvocations = mutableListOf<Evaluation>()
        val binding = createBinding(scriptArgs, computer, bindings, subInvocations)
        val compilerConfiguration = CompilerConfiguration.DEFAULT
        compilerConfiguration.scriptBaseClass = "io.kutoa.KutoaGroovyScript"
        val shell = GroovyShell(binding, compilerConfiguration)
        val result = shell.evaluate(source.value, identifier.toString())
        return Evaluation.applyFunction(term, bindings, Term.of(result),
                                        subInvocations)
    }

    private fun checkSyntax(args: kotlin.collections.List<Term>) {
        if (args.isEmpty() || args.size > 2) throw SyntaxError("$identifier must have one or two arguments")
    }

    private fun extractSource(args: kotlin.collections.List<Term>): TString {
        return args[0] as? TString ?: throw SyntaxError(
            "First argument to $identifier must be of type String")
    }

    private fun extractScriptArgs(args: kotlin.collections.List<Term>): TMap? {
        return if (args.size != 2) null
        else args[1] as? TMap ?: throw SyntaxError("Second argument to '$symbol' must be of type Map")
    }

    private fun createBinding(args: TMap?, computer: Computer, bindings: Bindings,
                              subInvocations: MutableList<Evaluation>): Binding {
        val binding = Binding()
        val argMap = mutableMapOf<String, Any>()
        args?.value?.forEach { key, term ->
            val keyword = key as? TKeyword ?: TKeyword(key.value.toString())
            val variableName = keyword.value.toVariableName()
            val value = term.unwrap()
            argMap[variableName] = value
            binding.setVariable(variableName, value)
        }
        binding.setVariable("arg", argMap.toMap())
        binding.setVariable("__computer", computer)
        binding.setVariable("__bindings", bindings)
        binding.setVariable("__substeps", subInvocations)
        return binding
    }

    companion object {

        val symbol = TSymbol("sys.scripting.groovy", "eval")

        fun application(script: String, vararg args : Pair<String, Any?>)
            = TApplication(symbol, listOf(TString(script)) + if (args.isNotEmpty()) listOf(
            Term.kmap(args.toMap())) else emptyList())

        private fun Keyword.toVariableName() : String = this.toString().replaceFirst(":", "")
    }
}