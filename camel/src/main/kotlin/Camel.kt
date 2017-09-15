import org.apache.camel.impl.DefaultCamelContext

/**
 * @author Ewan
 */
object Camel {
    val context = DefaultCamelContext()
    val producer = context.createProducerTemplate()!!
}

object CamelHttpClient : HttpResolver.Client {
    override fun get(url: Term.Atom.String): Term.Atom.String {
        val http4url = url.value.replaceFirst("://", "4://")
        return Term.Atom.String(Camel.producer.requestBody(http4url, null, String::class.java))
    }
}