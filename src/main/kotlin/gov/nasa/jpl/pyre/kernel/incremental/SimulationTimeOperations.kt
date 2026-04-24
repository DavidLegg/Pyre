package gov.nasa.jpl.pyre.kernel.incremental

object SimulationTimeOperations {
    // Define some convenience comparisons for the partial-order on causal time, which acknowledges parallel branches.
    // If t isCausallyBefore s, then t < s; but t < s does not imply t isCausallyBefore s (if t and s are on parallel branches).

    infix fun SimulationTime.isConcurrentWith(other: SimulationTime): Boolean =
        instant == other.instant && batch == other.batch && branch != other.branch

    infix fun SimulationTime.isCausallyBefore(other: SimulationTime): Boolean =
        this < other && !(this isConcurrentWith other)

    infix fun SimulationTime.isCausallyAfter(other: SimulationTime): Boolean =
        this > other && !(this isConcurrentWith other)

    infix fun SimulationTime.sameBranchAs(other: SimulationTime): Boolean =
        instant == other.instant && batch == other.batch && branch == other.branch

    // For brevity, also define these comparisons on the nodes themselves, deferring to their time fields.

    infix fun IncSimNode.isConcurrentWith(other: IncSimNode): Boolean =
        time isConcurrentWith other.time

    infix fun IncSimNode.isCausallyBefore(other: IncSimNode): Boolean =
        time isCausallyBefore other.time

    infix fun IncSimNode.isCausallyAfter(other: IncSimNode): Boolean =
        time isCausallyAfter other.time

    infix fun IncSimNode.sameBranchAs(other: IncSimNode): Boolean =
        time sameBranchAs other.time
}