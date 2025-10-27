package gov.nasa.jpl.pyre.general.monte_carlo

import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlin.math.nextDown

// TODO: Revamp this class, especially the duplication between all the initScope and TaskScope methods.

/**
 * Random number generator suitable for use within simulations.
 *
 * Copied largely from [kotlin.random.Random], with tweaks to run within a simulation.
 */
interface RandomNumberGenerator {
    /**
     * Create a new, independent [RandomNumberGenerator], seeded by this one.
     */
    context (scope: InitScope)
    fun split(): RandomNumberGenerator

    /**
     * Gets the next random [bitCount] number of bits.
     *
     * Generates an `Int` whose lower [bitCount] bits are filled with random values and the remaining upper bits are zero.
     *
     * @param bitCount number of bits to generate, must be in range 0..32, otherwise the behavior is unspecified.
     */
    context (scope: InitScope)
    fun nextBits(bitCount: Int): Int

    /**
     * Gets the next random `Int` from the random number generator.
     *
     * Generates an `Int` random value uniformly distributed between `Int.MIN_VALUE` and `Int.MAX_VALUE` (inclusive).
     */
    context (scope: InitScope)
    fun nextInt(): Int = nextBits(32)

    /**
     * Gets the next random non-negative `Int` from the random number generator less than the specified [until] bound.
     *
     * Generates an `Int` random value uniformly distributed between `0` (inclusive) and the specified [until] bound (exclusive).
     *
     * @param until must be positive.
     *
     * @throws IllegalArgumentException if [until] is negative or zero.
     */
    context (scope: InitScope)
    fun nextInt(until: Int): Int = nextInt(0, until)

    /**
     * Gets the next random `Int` from the random number generator in the specified range.
     *
     * Generates an `Int` random value uniformly distributed between the specified [from] (inclusive) and [until] (exclusive) bounds.
     *
     * @throws IllegalArgumentException if [from] is greater than or equal to [until].
     */
    context (scope: InitScope)
    fun nextInt(from: Int, until: Int): Int {
        require(from <= until)
        val n = until - from
        if (n > 0 || n == Int.MIN_VALUE) {
            val rnd = if (n and -n == n) {
                val bitCount = fastLog2(n)
                nextBits(bitCount)
            } else {
                var v: Int
                do {
                    val bits = nextInt().ushr(1)
                    v = bits % n
                } while (bits - v + (n - 1) < 0)
                v
            }
            return from + rnd
        } else {
            while (true) {
                val rnd = nextInt()
                if (rnd in from until until) return rnd
            }
        }
    }


    /**
     * Gets the next random `Long` from the random number generator.
     *
     * Generates a `Long` random value uniformly distributed between `Long.MIN_VALUE` and `Long.MAX_VALUE` (inclusive).
     */
    context (scope: InitScope)
    fun nextLong(): Long = nextInt().toLong().shl(32) + nextInt()

    /**
     * Gets the next random non-negative `Long` from the random number generator less than the specified [until] bound.
     *
     * Generates a `Long` random value uniformly distributed between `0` (inclusive) and the specified [until] bound (exclusive).
     *
     * @param until must be positive.
     *
     * @throws IllegalArgumentException if [until] is negative or zero.
     */
    context (scope: InitScope)
    fun nextLong(until: Long): Long = nextLong(0, until)

    /**
     * Gets the next random `Long` from the random number generator in the specified range.
     *
     * Generates a `Long` random value uniformly distributed between the specified [from] (inclusive) and [until] (exclusive) bounds.
     *
     * @throws IllegalArgumentException if [from] is greater than or equal to [until].
     */
    context (scope: InitScope)
    fun nextLong(from: Long, until: Long): Long {
        require(from <= until)
        val n = until - from
        if (n > 0) {
            val rnd: Long
            if (n and -n == n) {
                val nLow = n.toInt()
                val nHigh = (n ushr 32).toInt()
                rnd = when {
                    nLow != 0 -> {
                        val bitCount = fastLog2(nLow)
                        // toUInt().toLong()
                        nextBits(bitCount).toLong() and 0xFFFF_FFFF
                    }
                    nHigh == 1 ->
                        // toUInt().toLong()
                        nextInt().toLong() and 0xFFFF_FFFF
                    else -> {
                        val bitCount = fastLog2(nHigh)
                        nextBits(bitCount).toLong().shl(32) + (nextInt().toLong() and 0xFFFF_FFFF)
                    }
                }
            } else {
                var v: Long
                do {
                    val bits = nextLong().ushr(1)
                    v = bits % n
                } while (bits - v + (n - 1) < 0)
                rnd = v
            }
            return from + rnd
        } else {
            while (true) {
                val rnd = nextLong()
                if (rnd in from until until) return rnd
            }
        }
    }

    /**
     * Gets the next random [Boolean] value.
     */
    context (scope: InitScope)
    fun nextBoolean(): Boolean = nextBits(1) != 0

    /**
     * Gets the next random [Double] value uniformly distributed between 0 (inclusive) and 1 (exclusive).
     */
    context (scope: InitScope)
    fun nextDouble(): Double = doubleFromParts(nextBits(26), nextBits(27))

    /**
     * Gets the next random non-negative `Double` from the random number generator less than the specified [until] bound.
     *
     * Generates a `Double` random value uniformly distributed between 0 (inclusive) and [until] (exclusive).
     *
     * @throws IllegalArgumentException if [until] is negative or zero.
     */
    context (scope: InitScope)
    fun nextDouble(until: Double): Double = nextDouble(0.0, until)

    /**
     * Gets the next random `Double` from the random number generator in the specified range.
     *
     * Generates a `Double` random value uniformly distributed between the specified [from] (inclusive) and [until] (exclusive) bounds.
     *
     * [from] and [until] must be finite otherwise the behavior is unspecified.
     *
     * @throws IllegalArgumentException if [from] is greater than or equal to [until].
     */
    context (scope: InitScope)
    fun nextDouble(from: Double, until: Double): Double {
        require(from <= until)
        val size = until - from
        val r = if (size.isInfinite() && from.isFinite() && until.isFinite()) {
            val r1 = nextDouble() * (until / 2 - from / 2)
            from + r1 + r1
        } else {
            from + nextDouble() * size
        }
        return if (r >= until) until.nextDown() else r
    }

    /**
     * Gets the next random [Float] value uniformly distributed between 0 (inclusive) and 1 (exclusive).
     */
    context (scope: InitScope)
    fun nextFloat(): Float = nextBits(24) / (1 shl 24).toFloat()

    /**
     * Fills a subrange of the specified byte [array] starting from [fromIndex] inclusive and ending [toIndex] exclusive
     * with random bytes.
     *
     * @return [array] with the subrange filled with random bytes.
     */
    context (scope: InitScope)
    fun nextBytes(array: ByteArray, fromIndex: Int = 0, toIndex: Int = array.size): ByteArray {
        require(fromIndex in 0..array.size && toIndex in 0..array.size) { "fromIndex ($fromIndex) or toIndex ($toIndex) are out of range: 0..${array.size}." }
        require(fromIndex <= toIndex) { "fromIndex ($fromIndex) must be not greater than toIndex ($toIndex)." }

        val steps = (toIndex - fromIndex) / 4

        var position = fromIndex
        repeat(steps) {
            val v = nextInt()
            array[position] = v.toByte()
            array[position + 1] = v.ushr(8).toByte()
            array[position + 2] = v.ushr(16).toByte()
            array[position + 3] = v.ushr(24).toByte()
            position += 4
        }

        val remainder = toIndex - position
        val vr = nextBits(remainder * 8)
        for (i in 0 until remainder) {
            array[position + i] = vr.ushr(i * 8).toByte()
        }

        return array
    }

    /**
     * Fills the specified byte [array] with random bytes and returns it.
     *
     * @return [array] filled with random bytes.
     */
    context (scope: InitScope)
    fun nextBytes(array: ByteArray): ByteArray = nextBytes(array, 0, array.size)

    /**
     * Creates a byte array of the specified [size], filled with random bytes.
     */
    context (scope: InitScope)
    fun nextBytes(size: Int): ByteArray = nextBytes(ByteArray(size))


    /**
     * Gets the next random [bitCount] number of bits.
     *
     * Generates an `Int` whose lower [bitCount] bits are filled with random values and the remaining upper bits are zero.
     *
     * @param bitCount number of bits to generate, must be in range 0..32, otherwise the behavior is unspecified.
     */
    context (scope: TaskScope)
    suspend fun nextBits(bitCount: Int): Int

    /**
     * Gets the next random `Int` from the random number generator.
     *
     * Generates an `Int` random value uniformly distributed between `Int.MIN_VALUE` and `Int.MAX_VALUE` (inclusive).
     */
    context (scope: TaskScope)
    suspend fun nextInt(): Int = nextBits(32)

    /**
     * Gets the next random non-negative `Int` from the random number generator less than the specified [until] bound.
     *
     * Generates an `Int` random value uniformly distributed between `0` (inclusive) and the specified [until] bound (exclusive).
     *
     * @param until must be positive.
     *
     * @throws IllegalArgumentException if [until] is negative or zero.
     */
    context (scope: TaskScope)
    suspend fun nextInt(until: Int): Int = nextInt(0, until)

    /**
     * Gets the next random `Int` from the random number generator in the specified range.
     *
     * Generates an `Int` random value uniformly distributed between the specified [from] (inclusive) and [until] (exclusive) bounds.
     *
     * @throws IllegalArgumentException if [from] is greater than or equal to [until].
     */
    context (scope: TaskScope)
    suspend fun nextInt(from: Int, until: Int): Int {
        require(from <= until)
        val n = until - from
        if (n > 0 || n == Int.MIN_VALUE) {
            val rnd = if (n and -n == n) {
                val bitCount = fastLog2(n)
                nextBits(bitCount)
            } else {
                var v: Int
                do {
                    val bits = nextInt().ushr(1)
                    v = bits % n
                } while (bits - v + (n - 1) < 0)
                v
            }
            return from + rnd
        } else {
            while (true) {
                val rnd = nextInt()
                if (rnd in from until until) return rnd
            }
        }
    }


    /**
     * Gets the next random `Long` from the random number generator.
     *
     * Generates a `Long` random value uniformly distributed between `Long.MIN_VALUE` and `Long.MAX_VALUE` (inclusive).
     */
    context (scope: TaskScope)
    suspend fun nextLong(): Long = nextInt().toLong().shl(32) + nextInt()

    /**
     * Gets the next random non-negative `Long` from the random number generator less than the specified [until] bound.
     *
     * Generates a `Long` random value uniformly distributed between `0` (inclusive) and the specified [until] bound (exclusive).
     *
     * @param until must be positive.
     *
     * @throws IllegalArgumentException if [until] is negative or zero.
     */
    context (scope: TaskScope)
    suspend fun nextLong(until: Long): Long = nextLong(0, until)

    /**
     * Gets the next random `Long` from the random number generator in the specified range.
     *
     * Generates a `Long` random value uniformly distributed between the specified [from] (inclusive) and [until] (exclusive) bounds.
     *
     * @throws IllegalArgumentException if [from] is greater than or equal to [until].
     */
    context (scope: TaskScope)
    suspend fun nextLong(from: Long, until: Long): Long {
        require(from <= until)
        val n = until - from
        if (n > 0) {
            val rnd: Long
            if (n and -n == n) {
                val nLow = n.toInt()
                val nHigh = (n ushr 32).toInt()
                rnd = when {
                    nLow != 0 -> {
                        val bitCount = fastLog2(nLow)
                        // toUInt().toLong()
                        nextBits(bitCount).toLong() and 0xFFFF_FFFF
                    }
                    nHigh == 1 ->
                        // toUInt().toLong()
                        nextInt().toLong() and 0xFFFF_FFFF
                    else -> {
                        val bitCount = fastLog2(nHigh)
                        nextBits(bitCount).toLong().shl(32) + (nextInt().toLong() and 0xFFFF_FFFF)
                    }
                }
            } else {
                var v: Long
                do {
                    val bits = nextLong().ushr(1)
                    v = bits % n
                } while (bits - v + (n - 1) < 0)
                rnd = v
            }
            return from + rnd
        } else {
            while (true) {
                val rnd = nextLong()
                if (rnd in from until until) return rnd
            }
        }
    }

    /**
     * Gets the next random [Boolean] value.
     */
    context (scope: TaskScope)
    suspend fun nextBoolean(): Boolean = nextBits(1) != 0

    /**
     * Gets the next random [Double] value uniformly distributed between 0 (inclusive) and 1 (exclusive).
     */
    context (scope: TaskScope)
    suspend fun nextDouble(): Double = doubleFromParts(nextBits(26), nextBits(27))

    /**
     * Gets the next random non-negative `Double` from the random number generator less than the specified [until] bound.
     *
     * Generates a `Double` random value uniformly distributed between 0 (inclusive) and [until] (exclusive).
     *
     * @throws IllegalArgumentException if [until] is negative or zero.
     */
    context (scope: TaskScope)
    suspend fun nextDouble(until: Double): Double = nextDouble(0.0, until)

    /**
     * Gets the next random `Double` from the random number generator in the specified range.
     *
     * Generates a `Double` random value uniformly distributed between the specified [from] (inclusive) and [until] (exclusive) bounds.
     *
     * [from] and [until] must be finite otherwise the behavior is unspecified.
     *
     * @throws IllegalArgumentException if [from] is greater than or equal to [until].
     */
    context (scope: TaskScope)
    suspend fun nextDouble(from: Double, until: Double): Double {
        require(from <= until)
        val size = until - from
        val r = if (size.isInfinite() && from.isFinite() && until.isFinite()) {
            val r1 = nextDouble() * (until / 2 - from / 2)
            from + r1 + r1
        } else {
            from + nextDouble() * size
        }
        return if (r >= until) until.nextDown() else r
    }

    /**
     * Gets the next random [Float] value uniformly distributed between 0 (inclusive) and 1 (exclusive).
     */
    context (scope: TaskScope)
    suspend fun nextFloat(): Float = nextBits(24) / (1 shl 24).toFloat()

    /**
     * Fills a subrange of the specified byte [array] starting from [fromIndex] inclusive and ending [toIndex] exclusive
     * with random bytes.
     *
     * @return [array] with the subrange filled with random bytes.
     */
    context (scope: TaskScope)
    suspend fun nextBytes(array: ByteArray, fromIndex: Int = 0, toIndex: Int = array.size): ByteArray {
        require(fromIndex in 0..array.size && toIndex in 0..array.size) { "fromIndex ($fromIndex) or toIndex ($toIndex) are out of range: 0..${array.size}." }
        require(fromIndex <= toIndex) { "fromIndex ($fromIndex) must be not greater than toIndex ($toIndex)." }

        val steps = (toIndex - fromIndex) / 4

        var position = fromIndex
        repeat(steps) {
            val v = nextInt()
            array[position] = v.toByte()
            array[position + 1] = v.ushr(8).toByte()
            array[position + 2] = v.ushr(16).toByte()
            array[position + 3] = v.ushr(24).toByte()
            position += 4
        }

        val remainder = toIndex - position
        val vr = nextBits(remainder * 8)
        for (i in 0 until remainder) {
            array[position + i] = vr.ushr(i * 8).toByte()
        }

        return array
    }

    /**
     * Fills the specified byte [array] with random bytes and returns it.
     *
     * @return [array] filled with random bytes.
     */
    context (scope: TaskScope)
    suspend fun nextBytes(array: ByteArray): ByteArray = nextBytes(array, 0, array.size)

    /**
     * Creates a byte array of the specified [size], filled with random bytes.
     */
    context (scope: TaskScope)
    suspend fun nextBytes(size: Int): ByteArray = nextBytes(ByteArray(size))
}

context (context: InitScope)
fun RandomNumberGenerator(seed: Long): RandomNumberGenerator =
    XorWowRNG(context, seed.toInt(), seed.shr(32).toInt())

/** Takes upper [bitCount] bits (0..32) from this number. */
internal fun Int.takeUpperBits(bitCount: Int): Int =
    this.ushr(32 - bitCount) and (-bitCount).shr(31)

internal fun fastLog2(value: Int): Int = 31 - value.countLeadingZeroBits()

internal fun doubleFromParts(hi26: Int, low27: Int): Double =
    (hi26.toLong().shl(27) + low27) / (1L shl 53).toDouble()
