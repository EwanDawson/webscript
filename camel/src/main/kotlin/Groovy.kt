import groovy.lang.Binding
import groovy.lang.GroovyShell

/**
 * @author Ewan
 */
object Groovy {
    fun evaluate(script: String, context: Context): String {
        val binding = Binding(context.args)
        return GroovyShell(binding).evaluate(script)?.toString() ?: ""
    }
}