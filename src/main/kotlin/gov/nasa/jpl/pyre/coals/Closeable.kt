package gov.nasa.jpl.pyre.coals

data class Closeable<T>(val self: T, val closeAction: () -> Unit) : AutoCloseable {
    override fun close() = closeAction()

    companion object {
        fun <T> T.closesWith(closeAction: () -> Unit) = Closeable(this, closeAction)
    }
}