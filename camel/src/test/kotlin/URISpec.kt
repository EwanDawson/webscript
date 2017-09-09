import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.net.URI
import kotlin.test.assertEquals

/**
 * @author Ewan
 */
object URISpec: Spek({
    describe("parsing URI strings with java.net.URI") {
        on("parsing a standard HTTP URL") {
            val uri = URI("http://name:password@www.example.com:80/path/to/resource?queryParam1=myParam1&queryParam2=myParam2#fragment")
            it("parses the scheme") {
                assertEquals("http", uri.scheme)
            }
            it("parses the user info") {
                assertEquals("name:password", uri.userInfo)
            }
            it("parses the authority") {
                assertEquals("name:password@www.example.com:80", uri.authority)
            }
            it("parses the host") {
                assertEquals("www.example.com", uri.host)
            }
            it("parses the port") {
                assertEquals(80, uri.port)
            }
            it("parses the path") {
                assertEquals("/path/to/resource", uri.path)
            }
            it("parses the fragment") {
                assertEquals("fragment", uri.fragment)
            }
            it("parses the query string") {
                assertEquals("queryParam1=myParam1&queryParam2=myParam2", uri.query)
            }
        }
    }
})