package io.kutoa

import io.kotlintest.properties.Gen
import io.kotlintest.properties.forAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kutoa.Evaluation.Operation.*
import org.apache.camel.impl.DefaultCamelContext
import java.math.BigDecimal
import java.math.BigInteger

class Tests : StringSpec() {
    init {
        val camel = DefaultCamelContext()
        camel.start()
        val builtIns = listOf(
            Get(),
            Let(),
            List(),
            HttpGetFunction(camel.createFluentProducerTemplate()),
            GroovyScriptFunction()
        )
        val cache = Cache()
        val computer = Computer(builtIns, cache)

        infix fun Term.whenEvaluated (check: Evaluation.(Input) -> Boolean) = this.let { term ->
            val input = Input(term, kotlin.collections.emptyMap())
            computer.evaluate(term, kotlin.collections.emptyMap()).run { check(input) }
        }
        infix fun Input.whenEvaluated (check: Evaluation.(Input) -> Boolean) = this.let { input ->
            computer.evaluate(input.term, input.bindings).run { check(input) }
        }

        infix fun Term.shouldEvaluateTo (result: Evaluation) = this.let { term ->
            val input = Input(term, kotlin.collections.emptyMap())
            computer.evaluate(input.term, input.bindings) shouldBe result
        }

        infix fun Input.shouldEvaluateTo (result: Evaluation) = this.let { input ->
            computer.evaluate(input.term, input.bindings) shouldBe result
        }

        infix fun Term.withBindings (bindings: Bindings) : Input = Input(this, bindings)

        val matchesSpec: (Evaluation.(Input) -> Unit) -> Evaluation.(Input) -> Boolean
            = { check -> { input -> check(input); true } }

        val isConstantEvaluation : Evaluation.(Input) -> Boolean = matchesSpec { input ->
            result shouldBe input.term
            operation shouldBe CONSTANT
            subSteps.size shouldBe 0
            bindings shouldBe input.bindings
            dependencies shouldBe emptyMap()
        }

//        val isConstantCompoundEvaluation : Evaluation.(Term) -> Boolean = matchesSpec { input -> {
//            result == input && operation == COMPOUND && subSteps.size == (input as TCompound<*>).size
//                //&& subSteps.all { isConstantEvaluation(it.input)(it) }
//        }}

        val nl = System.lineSeparator()!!

        "Constant should evaluate to itself" {
            forAll(TConstants) {
                it whenEvaluated isConstantEvaluation
            }
        }

        "Set of Constants should evaluate to itself" {
            forAll(100, TSets(TConstants)) {
                it whenEvaluated isConstantEvaluation
            }
        }

        "List of Constants should evaluate to itself" {
            forAll(100, TLists(TConstants)) {
                it whenEvaluated isConstantEvaluation
            }
        }

        "Map with Constant keys and values should evaluate to itself" {
            forAll(100, TMaps(TConstants, TConstants)) {
                it whenEvaluated isConstantEvaluation
            }
        }

        "Symbol can be bound to binding" {
            val symbol = TSymbol("a")
            val value = TInteger(123)
            val bindings = mapOf(symbol to value)
            symbol withBindings bindings shouldEvaluateTo Evaluation(
                input = symbol,
                operation = BIND_SYMBOL,
                bindings = bindings,
                dependencies = bindings,
                subSteps = listOf(Evaluation.constant(value, bindings)),
                result = value
            )
        }

        "Error if Symbol is not present in bindings" {
            val a = TSymbol("a")
            a withBindings emptyMap() shouldEvaluateTo Evaluation (
                input = a,
                bindings = emptyMap(),
                result = TError(UnknownSymbolException(a)),
                operation = BIND_SYMBOL,
                dependencies = emptyMap(),
                subSteps = emptyList()
            )
        }

        "Error on recursive bind failure" {
            val a = TSymbol("a")
            val b = TSymbol("b")
            val context = mapOf(a to b)
            a withBindings context shouldEvaluateTo Evaluation(
                input = a,
                operation = BIND_SYMBOL,
                bindings = context,
                dependencies = context,
                subSteps = listOf(Evaluation(
                    input = b,
                    operation = BIND_SYMBOL,
                    bindings = context,
                    dependencies = emptyMap(),
                    subSteps = emptyList(),
                    result = TError(UnknownSymbolException(b))
                )),
                result = TError(UnknownSymbolException(b))
            )
        }

        "Function application with constant arguments" {
            val term = "(sys/list 1 2 3)".parseTerm() as TApplication
            val args = (1..3).map(Any::toTerm)
            term shouldEvaluateTo Evaluation(
                input = term,
                operation = APPLY_FUNCTION,
                bindings = emptyMap(),
                dependencies = emptyMap(),
                subSteps = args.map(Evaluation.Companion::constant),
                result = TList(args)
            )
        }

        "Function application is cached" {
            val args = (1..3).map { TInteger(it) }
            val term = TApplication(TSymbol("sys", "list"), args)
            cache.clear()
            val results = (1..2).map { computer.evaluate(term, emptyMap()) }
            results[0] shouldBe Evaluation(
                input = term,
                operation = APPLY_FUNCTION,
                bindings = emptyMap(),
                dependencies = emptyMap(),
                subSteps = args.map { integer -> Evaluation(
                    input = integer,
                    operation = CONSTANT,
                    bindings = emptyMap(),
                    dependencies = emptyMap(),
                    subSteps = emptyList(),
                    result = integer
                ) },
                result = TList(args)
            )
            results[1] shouldBe Evaluation(
                input = term,
                operation = CACHE_HIT,
                bindings = emptyMap(),
                dependencies = emptyMap(),
                subSteps = emptyList(),
                result = TList(args)
            )
        }

        "Basic Groovy script can be evaluated" {
            val term = "(sys.scripting.groovy/eval \"a + b\" {:a 1, :b 2})".parseTerm() as TApplication
            term shouldEvaluateTo Evaluation(
                input = term,
                operation = APPLY_FUNCTION,
                bindings = emptyMap(),
                dependencies = emptyMap(),
                result = 3.toTerm(),
                subSteps = term.args.map(Evaluation.Companion::constant)
            )
        }

        "Groovy script with name-spaced variables can be evaluated" {
            val script = "\"binding['local/a'] + binding['local/b']\""
            val term = "(sys.scripting.groovy/eval $script {:local/a 1, :local/b 2})".parseTerm() as TApplication
            term shouldEvaluateTo Evaluation(
                input = term,
                operation = APPLY_FUNCTION,
                bindings = emptyMap(),
                dependencies = emptyMap(),
                result = 3.toTerm(),
                subSteps = term.args.map(Evaluation.Companion::constant)
            )
        }

        "Evaluating a Groovy script with a syntax error gives an error result" {
            val term = "(sys.scripting.groovy/eval \"}{\" {})".parseTerm() as TApplication
            term shouldEvaluateTo Evaluation(
                input = term,
                operation = APPLY_FUNCTION,
                bindings = emptyMap(),
                dependencies = emptyMap(),
                result = TError("org.codehaus.groovy.control.MultipleCompilationErrorsException", "startup failed:\r\n" +
                    "sys.scripting.groovy/eval: 1: unexpected token: } @ line 1, column 1.$nl" +
                    "   }{$nl" +
                    "   ^$nl" +
                    nl +
                    "1 error$nl"),
                subSteps = term.args.map(Evaluation.Companion::constant)
            )
        }

        "Evaluating a Groovy script with a runtime error gives an error result" {
            val term = "(sys.scripting.groovy/eval \"1/0\" {})".parseTerm() as TApplication
            term shouldEvaluateTo Evaluation(
                input = term,
                operation = APPLY_FUNCTION,
                bindings = emptyMap(),
                dependencies = emptyMap(),
                result = TError("java.lang.ArithmeticException", "Division by zero"),
                subSteps = term.args.map(Evaluation.Companion::constant)
            )
        }

        "Can evaluate a Groovy script that calls out to another Groovy script" {
            val term = "(sys.scripting.groovy/eval \"5 * 'sys.scripting.groovy/eval'('5 * 5', [:])\" {})".parseTerm() as TApplication
            term shouldEvaluateTo Evaluation(
                input = term,
                operation = APPLY_FUNCTION,
                bindings = emptyMap(),
                dependencies = emptyMap(),
                result = 125.toTerm(),
                subSteps = term.args.map(Evaluation.Companion::constant) +
                    Evaluation(
                        input = GroovyScriptFunction.application("5 * 5"),
                        operation = APPLY_FUNCTION,
                        bindings = emptyMap(),
                        dependencies = emptyMap(),
                        result = 25.toTerm(),
                        subSteps = GroovyScriptFunction.application("5 * 5").args.map(Evaluation.Companion::constant)
                    )
            )
        }

        "Can make an HTTP get request" {

        }
    }
}

val TIntegers = TIntegerGen()
val TStrings = TStringGen()
val TDecimals = TDecimalGen()
val TCharacters = TCharacterGen()
val TBooleans = TBooleanGen()
val TKeywords = TKeywordGen()
val TConstants = TConstantGen()
val TSymbols = TSymbolGen()
val TSets = fun(termGen: Gen<Term>) = TSetGen(termGen)
val TLists = fun(termGen: Gen<Term>) = TListGen(termGen)
val TMaps = fun(keyGen: Gen<TConstant<*>>, valGen: Gen<Term>) = TMapGen(keyGen, valGen)

class TIntegerGen : Gen<TInteger> {
    override fun always(): Iterable<TInteger> = listOf<Number>(
        BigInteger.valueOf(Long.MIN_VALUE).times(BigInteger.TEN),
        Long.MIN_VALUE,
        Int.MIN_VALUE,
        -1,
        0,
        1,
        Int.MAX_VALUE,
        Long.MAX_VALUE,
        BigInteger.valueOf(Long.MAX_VALUE).times(BigInteger.TEN)
    ).map { TInteger(it) }

    override fun random(): Sequence<TInteger>
        = Gen.bigInteger(Int.MAX_VALUE).random().map { TInteger(it) }
}

class TStringGen : Gen<TString> {
    override fun always(): Iterable<TString>
        = Gen.string().always().map(::TString)

    override fun random(): Sequence<TString>
        = Gen.string().random().map(::TString)

}

class TDecimalGen : Gen<TDecimal> {
    override fun always(): Iterable<TDecimal> = listOf<Number>(
        BigDecimal.valueOf(Double.MIN_VALUE) * BigDecimal.TEN,
        Double.MIN_VALUE
        -1.0,
        0.0,
        1.0,
        Double.MAX_VALUE,
        BigDecimal.valueOf(Double.MAX_VALUE) * BigDecimal.TEN
    ).map { TDecimal(it) }

    override fun random(): Sequence<TDecimal>
        = Gen.double().random().map { TDecimal(it) }
}

class TCharacterGen : Gen<TCharacter> {
    override fun always(): Iterable<TCharacter>
        = Gen.string().always()
        .filter(String::isNotEmpty)
        .map(String::first)
        .map(::TCharacter)

    override fun random(): Sequence<TCharacter>
    = Gen.string().random()
        .filter(String::isNotEmpty)
        .map(String::first)
        .map(::TCharacter)

}

class TBooleanGen : Gen<TBoolean> {
    override fun always(): Iterable<TBoolean>
        = Gen.bool().always().map(::TBoolean)

    override fun random(): Sequence<TBoolean>
        = Gen.bool().random().map(::TBoolean)
}

class TKeywordGen : Gen<TKeyword> {
    private val regex = Regex("[;\"~()|\\\\\\[\\]`,'{}@/^]")

    override fun always(): Iterable<TKeyword>
        = random().take(10).asIterable()

    // TODO("Use custom generator, as Gen.string() is too slow")
    override fun random(): Sequence<TKeyword>
        = Gen.pair(Gen.string(), Gen.string()).random()
            .filter {
                it.toList().all {
                    it.isNotBlank() && it.first() in 'a'..'z'
                }
            }
            .map { Pair(it.first.replace(regex, ""), it.second.replace(regex, "")) }
            .map { TKeyword(it.first, it.second) }
}

class TConstantGen : Gen<TConstant<*>> {
    private val generators = listOf(TIntegers, TStrings, TDecimals, TCharacters, TBooleans, TKeywords)
    override fun always() = generators.map(Gen<TConstant<Any>>::always).reduce { acc, iterable -> acc.union(iterable) }
    override fun random() = object : Sequence<TConstant<*>> {
        override fun iterator() = object : Iterator<TConstant<*>> {
            override fun hasNext() = true
            override fun next() = generators.shuffled().first().random().iterator().next()
        }
    }
}

class TSymbolGen : Gen<TSymbol> {
    override fun always(): Iterable<TSymbol>
        = TKeywords.always().map { TSymbol(it.value.prefix, it.value.name) }

    override fun random(): Sequence<TSymbol>
        = TKeywords.random().map { TSymbol(it.value.prefix, it.value.name) }
}

class TSetGen<out T: Term>(private val termGen: Gen<T>) : Gen<TSet> {
    override fun always(): Iterable<TSet>
        = Gen.set(termGen).always().map(::TSet)

    override fun random(): Sequence<TSet>
        = Gen.set(termGen).random().map(::TSet)
}

class TListGen<out T: Term>(private val termGen: Gen<T>) : Gen<TList> {
    override fun always(): Iterable<TList>
        = Gen.list(termGen).always().map(::TList)

    override fun random(): Sequence<TList>
        = Gen.list(termGen).random().map(::TList)
}

class TMapGen<out K: TConstant<*>, out V: Term>(private val keyGen: Gen<K>, private val valGen: Gen<V>) : Gen<TMap> {
    override fun always(): Iterable<TMap>
        = Gen.map(keyGen, valGen).always().map { TMap(it.mapKeys { it.key }) }

    override fun random(): Sequence<TMap>
        = Gen.map(keyGen, valGen).random().map { TMap(it.mapKeys { it.key }) }

}

data class Input(val term: Term, val bindings: Bindings)