package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.kernel.BranchCellSet.BranchCell
import gov.nasa.jpl.pyre.kernel.CellSet.Cell
import gov.nasa.jpl.pyre.kernel.FinconCollector.Companion.within
import gov.nasa.jpl.pyre.kernel.InconProvider.Companion.within
import gov.nasa.jpl.pyre.kernel.NameOperations.asSequence
import gov.nasa.jpl.pyre.utilities.andThen
import kotlin.reflect.KType

typealias Effect<T> = (T) -> T

@Suppress("UNCHECKED_CAST")
sealed interface CellSet {
    // Cell is class, not data class, because we *want* to use object-identity equality
    class Cell<T> internal constructor(
        val name: Name,
        val valueType: KType,
        val stepBy: (T, Duration) -> T,
        val mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>,
    ) {
        override fun toString() = name.toString()
    }

    operator fun <T> get(cell: Cell<T>): T
    fun <T> emit(cell: Cell<T>, effect: Effect<T>)
}

class TrunkCellSet private constructor(
    private val map: MutableMap<Cell<*>, Any?>
) : CellSet {
    constructor() : this(mutableMapOf())

    fun <T : Any> allocate(
        name: Name,
        value: T,
        valueType: KType,
        stepBy: (T, Duration) -> T,
        mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>,
    ) = Cell(name, valueType, stepBy, mergeConcurrentEffects).also { map[it] = value }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(cell: Cell<T>): T = map.getValue(cell) as T

    @Suppress("UNCHECKED_CAST")
    override fun <T> emit(cell: Cell<T>, effect: Effect<T>) {
        map.compute(cell) { _, value -> effect(value as T) }
    }

    fun split(): BranchCellSet = BranchCellSet(this)

    private data class NetBranch<T>(val value: T?, val effect: Effect<T>)

    fun join(branches: Collection<BranchCellSet>) {
        // Collect the "net branch" for each modified cell
        // That'll be the cell from that branch, if only one task modified that cell,
        // or a net effect comprising all the branches' effects
        val netBranches: MutableMap<Cell<*>, NetBranch<*>> = mutableMapOf()
        @Suppress("UNCHECKED_CAST")
        fun <T> mergeBranch(handle: Cell<T>, branchCell: BranchCell<*>) {
            // Where possible, preserve the branch's value to just reuse that
            netBranches.merge(handle, NetBranch(branchCell.value as T, branchCell.effect as Effect<T>)) { (_, e), (_, f) ->
                // When merging multiple branches on one cell, discard the branches' cells and combine effects concurrently
                NetBranch(null, handle.mergeConcurrentEffects((e as Effect<T>), (f as Effect<T>)))
            }
        }
        for (branch in branches) {
            for ((handle, branchCell) in branch.edits) {
                mergeBranch(handle, branchCell)
            }
        }

        // Apply those net branches by either adopting the net cell, if available,
        // or applying the net effect to the trunk cell.
        @Suppress("UNCHECKED_CAST")
        fun <T> applyNetBranch(handle: Cell<*>, netBranch: NetBranch<T>) {
            map.compute(handle) { _, value ->
                netBranch.value ?: netBranch.effect(value as T)
            }
        }
        for ((handle, netBranch) in netBranches) {
            applyNetBranch(handle, netBranch)
        }
    }

    fun save(finconCollector: FinconCollector) {
        for ((cell, value) in map) {
            finconCollector
                .within(cell.name.asSequence())
                .report(value, cell.valueType)
        }
    }

    fun restore(inconProvider: InconProvider) {
        fun <T> restoreCell(handle: Cell<T>) = with(this[handle]) {
            // If incon is missing, ignore it and move on
            inconProvider.within(handle.name.asSequence()).provide<T>(handle.valueType)?.let {
                map[handle] = it
            }
        }
        map.keys.forEach { restoreCell(it) }
    }

    fun stepBy(delta: Duration) {
        @Suppress("UNCHECKED_CAST")
        fun <T> stepCell(cell: Cell<T>, value: Any?): T =
            cell.stepBy(value as T, delta)
        map.replaceAll { cell, value -> stepCell(cell, value) }
    }
}

class BranchCellSet private constructor(
    private val trunk: CellSet,
    val edits: MutableMap<Cell<*>, BranchCell<*>>,
) : CellSet {
    constructor(trunk: CellSet) : this(trunk, mutableMapOf())

    data class BranchCell<T>(val value: T, val effect: Effect<T>)

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(cell: Cell<T>): T =
        (edits[cell] as BranchCell<T>?)?.value ?: trunk[cell]

    @Suppress("UNCHECKED_CAST")
    override fun <T> emit(cell: Cell<T>, effect: Effect<T>) {
        edits.compute(cell) { _, branchCell ->
            if (branchCell == null) {
                // If we've never written to this cell before,
                // pull the cell from the trunk and record the effect.
                BranchCell(effect(trunk[cell]), effect)
            } else {
                // If we have written to this cell before,
                // apply this effect on top of the effect already there.
                BranchCell(
                    effect((branchCell as BranchCell<T>).value),
                    branchCell.effect andThen effect,
                )
            }
        }
    }
}
