import java.net.URI
import java.net.URLEncoder

/**
 * @author Ewan
 */

abstract class Term(open val value: Any?) {
    companion object {
        fun of(value: Any?): Term {
            return when {
                value == null -> nil
                value is String && isUri(value) && value.startsWith("value:", true) ->
                    Literal(value.replace("^value:".toRegex(RegexOption.IGNORE_CASE), ""))
                value is String && isUri(value) -> Identifier(value)
                else -> Literal(value)
            }
        }
        private val URI_REGEXP = "^[a-z][\\w+\\-]*:.+".toRegex()
        private fun isUri(value: String) = value.matches(URI_REGEXP)
    }
    abstract val uri: URI
    override fun toString() = "Term(${if (this != nil) "\"$value\"" else "" }})"
}

val nil = Literal(null)

data class Identifier internal constructor(override val value: String) : Term(value) {
    override val uri = URI(value)
}

data class Literal internal constructor(override val value: Any?, val type: String? = null) : Term(value) {
    override val uri = URI("value:${type?.let { "$type:" }?:""}${URLEncoder.encode(value?.toString()?:"\u0000", "UTF-8")}")
}