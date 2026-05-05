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

    // Various kinds of "increment" operations on SimulationTime, based on how KernelIncrementalSimulator chooses to use time.
    // As these don't have very strictly-defined meanings outside of how KernelIncrementalSimulator uses them, keep them internal.
    // In particular, KernelIncrementalSimulator chooses to alternate batches of cell updates and task updates.
    // This provides room to put cell stepping and merging between task batches, without needing to renumber batches for an edit.

    /** The next step; increments only the step field. */
    internal fun SimulationTime.nextStep() = copy(step = step + 1)

    /** Step 0 of the next task batch. Task batch numbers are always even. */
    internal fun SimulationTime.nextTaskBatch() =
        copy(batch = batch + 1 + ((batch + 1) % 2), step = 0)

    /** Step 0 of the next cell batch. Cell batch numbers are always odd. */
    internal fun SimulationTime.nextCellBatch() =
        copy(batch = batch + 1 + (batch % 2), step = 0)

    /** The earliest time of this batch, where branch and step are zero. */
    internal fun SimulationTime.batchStart() = copy(branch = 0, step = 0)

    /** Batch -1 of this time, when cells are stepped up in preparation for tasks starting in batch 0. */
    internal fun SimulationTime.cellSteppingBatch() =
        copy(batch = -1, branch = 0, step = 0)
}