package gov.nasa.jpl.pyre.general.monte_carlo

import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.general.monte_carlo.RandomNumberGenerator

/**
 * Random number generator, using Marsaglia's "xorwow" algorithm
 *
 * Cycles after 2^192 - 2^32 repetitions.
 *
 * For more details, see Marsaglia, George (July 2003). "Xorshift RNGs". Journal of Statistical Software. 8 (14). doi:10.18637/jss.v008.i14
 *
 * Available at https://www.jstatsoft.org/v08/i14/paper
 *
 * Implementation copied from [XorWowRandom](https://github.com/JetBrains/kotlin/blob/2.2.20/libraries/stdlib/src/kotlin/random/XorWowRandom.kt),
 * the default implementation of Kotlin's RNG.
 */
internal class XorWowRNG(context: InitScope, seed1: Int, seed2: Int) : RandomNumberGenerator {
    private var initState: XorWowState?
    private val state : MutableDiscreteResource<XorWowState>

    internal data class XorWowState(
        val x: Int,
        val y: Int,
        val z: Int,
        val w: Int,
        val v: Int,
        val addend: Int,
    ) {
        fun iterate(): Pair<Int, XorWowState> {
            // Equivalent to the xorxow algorithm
            // From Marsaglia, G. 2003. Xorshift RNGs. J. Statis. Soft. 8, 14, p. 5
            var t = x
            t = t xor (t ushr 2)
            t = (t xor (t shl 1)) xor v xor (v shl 4)
            val addend = addend + 362437
            return t + addend to XorWowState(y, z, w, v, t, addend)
        }
    }

    init {
        with (context) {
            initState = XorWowState(
                seed1,
                seed2,
                0,
                0,
                seed1.inv(),
                (seed1 shl 10) xor (seed2 ushr 4)
            )
            with (initState!!) {
                require((x or y or z or w or v) != 0) { "Initial state must have at least one non-zero element." }
            }

            // some trivial seeds can produce several values with zeroes in upper bits, so we discard first 64
            repeat(64) { nextInt() }

            state = discreteResource("rng_state", initState!!)
        }
    }

    context(scope: InitScope)
    override fun split(): RandomNumberGenerator =
        XorWowRNG(scope, nextInt(), nextInt())

    context(scope: InitScope)
    override fun nextInt(): Int =
        requireNotNull(initState).iterate().also { initState = it.second }.first

    context(scope: InitScope)
    override fun nextBits(bitCount: Int): Int = nextInt().takeUpperBits(bitCount)

    context(scope: TaskScope)
    override suspend fun nextInt(): Int {
        initState?.let { state.set(it); initState = null }
        return state.getValue().iterate().also { state.set(it.second) }.first
    }

    context(scope: TaskScope)
    override suspend fun nextBits(bitCount: Int): Int = nextInt().takeUpperBits(bitCount)
}