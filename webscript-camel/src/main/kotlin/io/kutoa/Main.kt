package io.kutoa

import org.apache.camel.impl.DefaultCamelContext

fun main(args: Array<String>) {
    val context = DefaultCamelContext()
    context.start()
    val builtIns = listOf(
        GetFunction(),
        LetFunction(),
        HttpGetFunction(context.createFluentProducerTemplate()),
        GroovyScriptFunction()
    )
    val computer = Computer(builtIns, Cache())
    println(computer.evaluate(Term.parse("test/a"), mapOf(TSymbol("test", "a") to TInteger(123))))
}