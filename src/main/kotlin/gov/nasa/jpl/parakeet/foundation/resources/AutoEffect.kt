package gov.nasa.jpl.parakeet.foundation.resources

import gov.nasa.jpl.pyre.kernel.Effect

/**
 * An [Effect] which automatically handles commutativity by brute-force checking.
 *
 * This class is used to handle infrequent concurrent effects,
 * accepting a usually-small hit to performance and infrequent runtime errors
 * for much greater modeler convenience.
 *
 * Concurrent effects fault a resource if and only if the concurrent effects do not commute.
 * Otherwise, the effects are applied in an arbitrary order as the merged effect.
 */
class AutoEffect<D>(val subeffects: List<ResourceEffect<D>>) : ResourceEffect<D> {
    override fun invoke(p1: Result<FullDynamics<D>>): Result<FullDynamics<D>> = Result.runCatching {
        // Run the whole merging/checking procedure in a runCatching block.
        // If some ordering of effects faults the cell, we can throw that exception and fail fast.
        // Similarly, if some ordering of effects differs from the first ordering, we throw and fail fast.

        if (subeffects.size > MAX_SUBEFFECTS_WARNING_THRESHOLD) {
            // Warn the user if we're about to do something truly excessive...
            System.err.println("${subeffects.size} concurrent effects present in a single ${AutoEffect::class.simpleName} merge.")
            System.err.println(
                LongRange(1, subeffects.size.toLong()).reduce { x, y -> x * y }.toString() +
                        " orderings need to be tested, which may require excessive time."
            )
            System.err.println("Consider using a custom effect type, or reworking model to reduce concurrent effects.")
            System.err.println("Effects to be merged:")
            subeffects.forEach { System.err.println("  $it") }
        }

        // First, compute the result from the first ordering of effects.
        // So long as every ordering produces this value, we can just return this value.
        // By default, every effect uses mapCatching internally to catch faults.
        // So, if there's an ordering of effects that could fault and recover the cell, that should be preserved.
        val firstResult = subeffects.fold(p1) { value, effect -> effect(value) }.getOrThrow()

        // TODO: Add a size=2 optimization that just checks the other ordering directly

        // Copy the subeffects to an array.
        // We'll juggle the pointers in this array to work through all permutations of subeffects efficiently.
        val effectArray = subeffects.toTypedArray()

        /** Run block on every permutation of effects at or after n */
        fun runPermutations(n: Int, block: () -> Unit) {
            if (n >= effectArray.size - 1) block()
            else {
                val element_n = effectArray[n]
                runPermutations(n + 1) {
                    // effectArray[n+1:] is shuffled.
                    // Run the block without shuffling element n in.
                    block()
                    // swap element n into each position in this arrangement and run block on each
                    for (m in n + 1..<effectArray.size) {
                        effectArray[m - 1] = effectArray[m]
                        effectArray[m] = element_n
                        block()
                    }
                    // Finally, put everything back where it was so the rest of the shuffling works as expected
                    for (m in effectArray.size - 1 downTo n + 1) {
                        effectArray[m] = effectArray[m - 1]
                    }
                    effectArray[n] = element_n
                }
            }
        }

        runPermutations(0) {
            val thisResult = effectArray.fold(p1) { value, effect -> effect(value) }.getOrThrow()
            // TODO: Allow us to parameterize equality check here with a more tolerant "equals" function.
            require(firstResult == thisResult) {
                "Non-commuting concurrent effects. ${AutoEffect::class.simpleName} detected different results ($firstResult vs. $thisResult) " +
                        "from ordering (${subeffects.joinToString(" | ") { it.toString() }}) vs. (${effectArray.joinToString(" | ") { it.toString() }})"
            }
        }

        // If we reached this point, all the effects commute.
        firstResult
    }

    companion object {
        private val MAX_SUBEFFECTS_WARNING_THRESHOLD = 6

        /**
         * Merge two general effects using [AutoEffect].
         *
         * If all concurrent effect merging is done with this method, the result is a flat [AutoEffect].
         * Note that by only using [AutoEffect] once we're merging two or more effects, we incur no performance penalty
         * in the most-common case of a single effect.
         */
        fun <D> autoMerge(e1: ResourceEffect<D>, e2: ResourceEffect<D>) = when {
            e1 is AutoEffect<D> && e2 is AutoEffect<D> -> AutoEffect(e1.subeffects + e2.subeffects)
            e1 is AutoEffect<D> -> AutoEffect(e1.subeffects + e2)
            e2 is AutoEffect<D> -> AutoEffect(e2.subeffects + e1)
            else -> AutoEffect(listOf(e1, e2))
        }
    }
}
