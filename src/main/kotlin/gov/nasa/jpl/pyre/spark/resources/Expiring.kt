package gov.nasa.jpl.pyre.spark.resources

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.*
import gov.nasa.jpl.pyre.spark.BasicSerializers.alias
import gov.nasa.jpl.pyre.spark.BasicSerializers.nullable
import gov.nasa.jpl.pyre.spark.resources.Expiry.Companion.NEVER

data class Expiring<T>(val data: T, val expiry: Expiry)

fun <D : Dynamics<*, D>> Expiring<D>.step(time: Duration) = Expiring(data.step(time), expiry - time)

data class Expiry(val time: Duration?): Comparable<Expiry> {
    override fun compareTo(other: Expiry): Int {
        return if (this.time == null && other.time == null) 0
        else if (this.time == null) 1
        else if (other.time == null) -1
        else this.time.compareTo(other.time)
    }

    override fun toString(): String {
        return this.time?.toString() ?: "NEVER"
    }

    companion object {
        val NOW = Expiry(Duration.ZERO)
        val NEVER = Expiry(null)
        fun serializer() = nullable(Duration.serializer()).alias(InvertibleFunction.of(Expiry::time, ::Expiry))
    }
}

operator fun Expiry.plus(other: Expiry) = Expiry(other.time?.let { this.time?.plus(it) })
operator fun Expiry.plus(other: Duration) = Expiry(this.time?.plus(other))
operator fun Expiry.minus(other: Duration) = Expiry(this.time?.minus(other))
infix fun Expiry.or(other: Expiry) = minOf(this, other)

object ExpiringMonad {
    fun <A> pure(a: A): Expiring<A> = Expiring(a, NEVER)
    fun <A, B> apply(a: Expiring<A>, f: Expiring<(A) -> B>) =
        Expiring(f.data(a.data), f.expiry or a.expiry)
    fun <A> join(a: Expiring<Expiring<A>>) = Expiring(a.data.data, a.expiry or a.data.expiry)
    // TODO: Generate other methods
    suspend fun <A, B> apply(a: Expiring<A>, f: Expiring<suspend (A) -> B>) =
        Expiring(f.data(a.data), f.expiry or a.expiry)
    fun <A, B> apply(f: Expiring<(A) -> B>): (Expiring<A>) -> Expiring<B> = { apply(it, f) }
    fun <A, B> map(a: Expiring<A>, f: (A) -> B): Expiring<B> = apply(a, pure(f))
    suspend fun <A, B> map(a: Expiring<A>, f: suspend (A) -> B): Expiring<B> = apply(a, pure(f))
    fun <A, B> bind(a: Expiring<A>, f: (A) -> Expiring<B>): Expiring<B> = join(map(a, f))
}
