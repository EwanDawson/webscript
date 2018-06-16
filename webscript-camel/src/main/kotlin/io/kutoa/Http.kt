package io.kutoa

import org.apache.camel.FluentProducerTemplate

class HttpGetFunction(private val template: FluentProducerTemplate) : Function(
    TSymbol("sys.net.http", "get")) {
    override fun apply(term: TApplication, bindings: Bindings, computer: Computer): Evaluation {
        if (term.args.size != 1) throw SyntaxError("$identifier requires argument")
        val options = term.args[0] as? TMap ?: throw SyntaxError(
            "Argument to $identifier must be a Map")
        val url = options.value[TKeyword("url")] as? TString ?: throw SyntaxError(
            "Options argument to $identifier must include an entry with 'url'")
        var request = template.to(url.value)
        val headers = (options.value[TKeyword("headers")] as? TMap)?.unwrap()
        headers?.forEach { k, v -> request = request.withHeader(k.toString(), v) }
        val result = TString(request.request(String::class.java))
        return Evaluation.applyFunction(term, bindings, result)
    }
}