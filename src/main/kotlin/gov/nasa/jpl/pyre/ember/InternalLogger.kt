package gov.nasa.jpl.pyre.ember

/**
 * Logger for use by the simulator itself.
 * Output from this logger is not meant to be viewed by users of Pyre, and should only be used to debug Pyre itself.
 */
object InternalLogger {
    private val indent = "  "
    private var indentLevel = 0

    fun log(msg: String) {
        println(indent.repeat(indentLevel) + msg)
    }

    fun indent() {
        indentLevel++
    }

    fun unindent() {
        indentLevel--
    }

    fun startBlock(msg: String? = null) {
        msg?.let(::log)
        indent()
    }

    fun endBlock(msg: String? = null) {
        unindent()
        msg?.let(::log)
    }

    fun block(startMsg: String? = null, endMsg: String? = null, block: () -> Unit) {
        startBlock(startMsg)
        block()
        endBlock(endMsg)
    }
}