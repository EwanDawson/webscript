import org.apache.camel.impl.DefaultCamelContext
import java.util.concurrent.CompletableFuture

/**
 * @author Ewan
 */
object Camel {
    val context = DefaultCamelContext()
    val producer = context.createProducerTemplate()!!
}

object CamelHttpClient : HttpResolver.Client {
    override fun get(url: String): CompletableFuture<String> {
        val http4url = url.replaceFirst("://", "4://")
        return Camel.producer.asyncRequestBody(http4url, null, String::class.java)
    }
}