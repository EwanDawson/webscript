import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import us.bpsm.edn.Keyword
import us.bpsm.edn.Symbol
import java.net.URI
import java.time.Instant
import kotlin.test.assertEquals

/**
 * @author Ewan
 */

object TermSpec: Spek({
    describe("parsing of EDN to Terms") {
        assertEquals(
            null, Term("nil"))
        assertEquals(
            true, Term("true"))
        assertEquals(
            false, Term("false"))
        assertEquals(
            "my string", Term("\"my string\""))
        assertEquals(
            -123L, Term("-123"))
        assertEquals(
            ' ', Term("\\space"))
        assertEquals(
            -8.98693639639e12, Term("-8.98693639639e12"))
        assertEquals(
            listOf(1L, 4L, 2L, 3L), Term("[1,4,2,3]"))
        assertEquals(
            mapOf("a" to 1L, "b" to 2L), Term("{\"a\"1\"b\"2}"))
        assertEquals(
            setOf(true, false), Term("#{true false}"))
        assertEquals(
            URI("http://www.example.com"),
            Term("#uri \"http://www.example.com\"")
        )
        assertEquals(
            Instant.parse("2017-09-09T23:25:00.000Z"),
            Term("#inst \"2017-09-09T23:25:00.000Z\"")
        )
        assertEquals(
            FnIdentifier(Symbol.newSymbol("net.lazygun", "math.double")),
            Term("#fn net.lazygun/math.double")
        )
        assertEquals(
            FnInlineScript("groovy", "println('hello world')"),
            Term("#lang/groovy \"println('hello world')\"")
        )
        assertEquals(
            FnURIScript("groovy", URI("http://www.example.com")),
            Term("#lang/groovy #uri \"http://www.example.com\"")
        )
        assertEquals(
            FnApplication(FnIdentifier(Symbol.newSymbol("net.lazygun", "math.double")), mapOf(Keyword.newKeyword("x") to "abc", Keyword.newKeyword("y") to 123L)),
            Term("(#fn net.lazygun/math.double :x \"abc\" :y 123)")
        )
        // TODO("Function application should just be an identifier as the first item of a list, e.g. (net.lazygun.math/double {:x \"abc\" :y 123})")
        // TODO("Function arguments should be either a single map (for named args) or a single vector (for positional args)")
        // TODO("Script evaluation should be just another function, e.g. (sys.scripting.groovy/evaluate \"println('hello world')\")")
        // TODO("URI resolving should be just another function e.g. (sys.http/get \"http://www.example.com\")")
    }
})