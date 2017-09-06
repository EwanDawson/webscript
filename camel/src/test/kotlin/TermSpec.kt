import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @author Ewan
 */

object TermSpec: Spek({
    describe("a term") {
        on("create with null value") {
            val term = Term.of(null)
            it ("should be a Literal") {
                assertTrue(term is Literal)
            }
            it("should be the nil term") {
                assertEquals(nil, term)
            }
            it("has value '\\u0000'") {
                assertEquals("\u0000", term.value)
            }
            it("has the uri 'value:%00'") {
                assertEquals("value:%00", term.uri.toString())
            }
        }
        on("create with a json literal value") {
            val term = Term.of("{\"id\":123}")
            it("should be of type Literal") {
                assertTrue(term is Literal)
            }
            it("has value '{\"id\":123}'") {
                assertEquals("{\"id\":123}", term.value)
            }
            it("has the uri 'value:{\"id\":123}'") {
                assertEquals("value:%7B%22id%22%3A123%7D", term.uri.toString())
            }
        }
        on("create with a uri with value: scheme") {
            val term = Term.of("value:123.456")
            it("should be of type Literal") {
                assertTrue(term is Literal)
            }
            val literal = term as Literal
            it("has value 123.456") {
                assertEquals("123.456", literal.value)
            }
            it("has the uri 'value:123.456'") {
                assertEquals("value:123.456", literal.uri.toString())
            }
        }
        on("create a term with an http uti") {
            val term = Term.of("http://example.com?id=123#head")
            it("is of type Identifier") {
                assertTrue(term is Identifier)
            }
            it("has value 'http://example.com?id=123#head") {
                assertEquals("http://example.com?id=123#head", term.value)
            }
            it("has a uri equal to its value") {
                assertEquals(term.value, term.uri.toString())
            }
        }
    }
})