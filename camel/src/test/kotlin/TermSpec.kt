import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @author Ewan
 */

object TermSpec: Spek({
    describe("parsing of EDN to Terms") {
        on("nil") {
            val term = Term.of("nil")
            it ("parses to Nil") {
                assertTrue(term is Nil)
            }
            it ("has the correct value") {
                assertEquals(null, (term as Nil).value)
            }
            it ("reports the correct edn representation") {
                assertEquals("nil", term.term)
            }
        }
        on("true") {
            val term = Term.of("true")
            it("parses to True") {
                assertTrue(term is True)
            }
            it ("has the correct value") {
                assertEquals(true, (term as Bool).value)
            }
            it ("reports the correct edn representation") {
                assertEquals("true", term.term)
            }
        }
        on("false") {
            val term = Term.of("false")
            it("parses to False") {
                assertTrue(term is False)
            }
            it ("has the correct value") {
                assertEquals(false, (term as Bool).value)
            }
            it ("reports the correct edn representation") {
                assertEquals("false", term.term)
            }
        }
        on("a string") {
            val term = Term.of("\"my string\"")
            it("parses to a StringTerm") {
                assertTrue(term is StringTerm)
            }
            it ("has the correct value") {
                assertEquals("my string", (term as StringTerm).value)
            }
            it ("reports the correct edn representation") {
                assertEquals("\"my string\"", term.term)
            }
        }
        on("an integer") {
            val term = Term.of("12345678912345678900000000")
            it ("parses to an IntTerm") {
                assertTrue(term is IntTerm)
            }
            it ("has the correct value") {
                assertEquals(BigInteger("12345678912345678900000000"), (term as IntTerm).value)
            }
            it ("reports the correct edn representation") {
                assertEquals("12345678912345678900000000", term.term)
            }
        }
        on ("a character") {
            val term = Term.of("\\space")
            it ("parses to a CharTerm") {
                assertTrue(term is CharTerm)
            }
            it ("has the correct value") {
                assertEquals(' ', (term as CharTerm).value)
            }
            it ("reports the correct edn representation") {
                assertEquals("\\space", term.term)
            }
        }
        on ("a real number") {
            val term = Term.of("-8.98693639639e12")
            it ("parses to a Real") {
                assertTrue(term is Real)
            }
            it ("has the correct value") {
                assertEquals(BigDecimal("-8.98693639639e12"), (term as Real).value)
            }
            it ("reports the correct edn representation") {
                assertEquals("-8.98693639639e12", term.term)
            }
        }
        on ("an edn vector of integers") {
            val term = Term.of("[1, 2, 3, 4]")
            it ("parses to an ArrayTerm") {
                assertTrue(term is ArrayTerm)
            }
            it ("contains four IntTerm objects") {
                assertEquals(listOf(true, true, true, true), (term as ArrayTerm).value.map { it is IntTerm })
            }
            it ("contains the correct values in the correct order") {
                assertEquals(listOf(1, 2, 3, 4).map { BigInteger("$it") }, (term as ArrayTerm).value.map { (it as IntTerm).value })
            }
        }
    }
})