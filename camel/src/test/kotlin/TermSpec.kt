import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.on
import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import java.net.URI
import java.time.Instant
import kotlin.test.assertEquals

/**
 * @author Ewan
 */

object TermSpec: Spek({
    fun test(edn: String, expected: Any?) {
        on(edn) {
            assertEquals(expected, Term(edn))
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
        test("#uri \"http://www.example.com\"",URI("http://www.example.com"))
        test("#inst \"2017-09-09T23:25:00.000Z\"",Instant.parse("2017-09-09T23:25:00.000Z"))
        test("net.lazygun/math.double",Symbol.newSymbol("net.lazygun", "math.double"))
        test("(net.lazygun/math.double)",FnApplicationPositionalArgs(FnIdentifier(Symbol.newSymbol("net.lazygun", "math.double"))))
        test("(net.lazygun/math.double [123])",FnApplicationPositionalArgs(FnIdentifier(Symbol.newSymbol("net.lazygun", "math.double")), listOf(123L)))
        test("(net.lazygun/math.double {:x \"abc\" :y 123})",FnApplicationNamedArgs(FnIdentifier(Symbol.newSymbol("net.lazygun", "math.double")), mapOf(Keyword.newKeyword("x") to "abc", Keyword.newKeyword("y") to 123L)))
    }
})