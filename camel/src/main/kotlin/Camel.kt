import org.apache.camel.impl.DefaultCamelContext

/**
 * @author Ewan
 */
object Camel {
    val context = DefaultCamelContext()
    val producer = context.createProducerTemplate()!!
}