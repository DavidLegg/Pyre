package gov.nasa.jpl.pyre.coals

/**
 * Add closing behavior to an arbitrary object.
 * Use [Closeable.use] to mimic [AutoCloseable.use] from the Kotlin library.
 */
data class Closeable<out T>(val self: T, val closeAction: () -> Unit) : AutoCloseable {
    override fun close() = closeAction()

    companion object {
        fun <T> T.closesWith(closeAction: () -> Unit): Closeable<T> = Closeable(this, closeAction)
        fun <T : AutoCloseable> T.asCloseable(): Closeable<T> = closesWith(this::close)
        fun <T, R> Closeable<T>.use(block: (T) -> R): R = this.use { it: Closeable<T> -> block(it.self) }
    }
}