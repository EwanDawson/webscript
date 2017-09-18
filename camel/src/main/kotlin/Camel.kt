import org.apache.camel.impl.DefaultCamelContext

/**
 * @author Ewan
 */
object Camel {
    val context = DefaultCamelContext()
    val producer = context.createProducerTemplate()!!
}

object CamelHttpClient : HttpResolver.Client {
    override fun get(url: String): String {
        val http4url = url.replaceFirst("://", "4://")
        return Camel.producer.requestBody(http4url, null, String::class.java)
    }
}