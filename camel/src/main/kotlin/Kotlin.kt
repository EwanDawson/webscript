import javax.script.ScriptEngineManager

/**
 * @author Ewan
 */
fun main(args: Array<String>) {
    val factory = ScriptEngineManager().getEngineByExtension("kts").factory
    val result = factory.scriptEngine.eval("2 + 2")
    println(result)
}