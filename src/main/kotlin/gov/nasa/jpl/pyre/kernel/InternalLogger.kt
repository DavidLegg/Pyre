package gov.nasa.jpl.pyre.kernel

/**
 * Logger for use by the simulator itself.
 * Output from this logger is not meant to be viewed by users of Pyre, and should only be used to debug Pyre itself.
 */
object InternalLogger {
    private const val ENABLED: Boolean = false
    private const val INDENT = "  "
    private var indentLevel = 0

    fun log(msg: () -> String) {
        if (ENABLED) println(INDENT.repeat(indentLevel) + msg())
    }

    fun indent() {
        indentLevel++
    }

    fun unindent() {
        indentLevel--
    }

    fun startBlock(msg: (() -> String)? = null) {
        msg?.let(::log)
        indent()
    }

    fun endBlock(msg: (() -> String)? = null) {
        unindent()
        msg?.let(::log)
    }

    fun <R> block(startMsg: (() -> String)? = null, endMsg: ((R) -> String)? = null, block: () -> R): R {
        startBlock(startMsg)
        val result = block()
        endBlock(endMsg?.let { { it(result) } })
        return result
    }
}