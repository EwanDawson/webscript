import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.on
import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import kotlin.test.assertEquals

/**
 * @author Ewan
 */

object TermSpec: Spek({
    fun test(edn: String, expected: Any?) {
        on(edn) {
            assertEquals(Term.of(expected), Term.parse(edn))
        }
    }
    describe("parsing of EDN to terms") {
        test("nil", null)
        test("true", true)
        test("false", false)
        test("\"my string\"", "my string")
        test("-123", -123L)
        test("\\space",' ')
        test("-8.98693639639e12",-8.98693639639e12)
        test("[1,4,2,3]",listOf(1L, 4L, 2L, 3L))
        test("{\"a\"1\"b\"2}",mapOf("a" to 1L, "b" to 2L))
        test("#{true false}",setOf(true, false))
        test("net.lazygun/math.double",Symbol.newSymbol("net.lazygun", "math.double"))
        test("(net.lazygun/math.double)",Term.FunctionApplication(Term.Atom.Function(Symbol.newSymbol("net.lazygun", "math.double"))))
        test("(net.lazygun/math.double {:x \"abc\" :y 123})",Term.FunctionApplication(Term.Atom.Function(Symbol.newSymbol("net.lazygun", "math.double")), Term.Container.Map(mapOf(Term.of(Keyword.newKeyword("x")) to Term.of("abc"), Term.of(Keyword.newKeyword("y")) to Term.of(123L)))))
    }
})