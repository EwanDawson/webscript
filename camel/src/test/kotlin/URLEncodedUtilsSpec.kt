import org.apache.http.client.utils.URLEncodedUtils
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @author Ewan
 */
object URLEncodedUtilsSpec: Spek ({
    describe("URL parsing with URLEncodedUtils") {
        on("a URL with two unique query parameters") {
            val url = URI("http://example.com?a=1&b=2")
            val params = URLEncodedUtils.parse(url, StandardCharsets.UTF_8)
            it("should parse to a list of 2 NameValuePairs") {
                val expected = listOf(Pair("a", "1"), Pair("b", "2"))
                val actual = params.map { Pair(it.name, it.value) }
                assertEquals(expected, actual)
            }
        }
        on("a URL with no query parameters") {
            val url = URI("http://example.com")
            val params = URLEncodedUtils.parse(url, StandardCharsets.UTF_8)
            it("should parse to an empty list") {
                assertTrue(params.isEmpty())
            }
        }
        on("a URL with two query parameters with the same name and different values") {
            val url = URI("http://example.com?a=1&a=2")
            val params = URLEncodedUtils.parse(url, StandardCharsets.UTF_8)
            it("should parse to a list of 2 NameValuePairs") {
                val expected = listOf(Pair("a", "1"), Pair("a", "2"))
                val actual = params.map { Pair(it.name, it.value) }
                assertEquals(expected, actual)
            }
        }
    }
})