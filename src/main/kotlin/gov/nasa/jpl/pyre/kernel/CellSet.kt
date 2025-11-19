package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.kernel.BranchCellSet.BranchCell
import gov.nasa.jpl.pyre.kernel.CellSet.CellHandle
import gov.nasa.jpl.pyre.kernel.FinconCollector.Companion.within
import gov.nasa.jpl.pyre.kernel.InconProvider.Companion.within
import gov.nasa.jpl.pyre.kernel.NameOperations.asSequence
import gov.nasa.jpl.pyre.utilities.andThen
import kotlin.reflect.KType

@Suppress("UNCHECKED_CAST")
sealed interface CellSet {
    // CellHandle is class, not data class, because we *want* to use object-identity equality
    class CellHandle<T>(val name: Name, val valueType: KType) {
        override fun toString() = name.toString()
    }

    operator fun <T> get(cellHandle: CellHandle<T>): Cell<T>
    fun <T> emit(cellHandle: CellHandle<T>, effect: Effect<T>)
}

class TrunkCellSet private constructor(
    private val map: MutableMap<CellHandle<*>, Cell<*>>
) : CellSet {
    constructor() : this(mutableMapOf())

    fun <T : Any> allocate(cell: Cell<T>) =
        CellHandle<T>(cell.name, cell.valueType).also { map[it] = cell }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(cellHandle: CellHandle<T>): Cell<T> {
        return map.getValue(cellHandle) as Cell<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> emit(
        cellHandle: CellHandle<T>,
        effect: Effect<T>
    ) {
        map.compute(cellHandle) { _, cell ->
            (cell as Cell<T>).applyEffect(effect)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> emitUnchecked(cellHandle: CellHandle<T>, effect: Effect<*>) =
        emit(cellHandle, effect as Effect<T>)

    fun split(): BranchCellSet = BranchCellSet(this)

    private data class NetBranch<T>(val cell: Cell<T>?, val effect: Effect<T>)

    fun join(branches: Collection<BranchCellSet>) {
        // Collect the "net branch" for each modified cell
        // That'll be the cell from that branch, if only one task modified that cell,
        // or a net effect comprising all the branches' effects
        val netBranches: MutableMap<CellHandle<*>, NetBranch<*>> = mutableMapOf()
        @Suppress("UNCHECKED_CAST")
        fun <T> mergeBranch(handle: CellHandle<*>, branchCell: BranchCell<T>) {
            // Where possible, preserve the branch's cell to just reuse that
            netBranches.merge(handle, NetBranch(branchCell.cell, branchCell.effect)) { (_, e), (_, f) ->
                // When merging multiple branches on one cell, discard the branches' cells and combine effects concurrently
                NetBranch(null, branchCell.cell.mergeConcurrentEffects((e as Effect<T>), (f as Effect<T>)))
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
        fun <T> applyNetBranch(handle: CellHandle<*>, netBranch: NetBranch<T>) {
            map.compute(handle) { _, cell ->
                netBranch.cell ?: (cell as Cell<T>).applyEffect(netBranch.effect)
            }
        }
        for ((handle, netBranch) in netBranches) {
            applyNetBranch(handle, netBranch)
        }
    }

    fun save(finconCollector: FinconCollector) {
        fun <T> saveCell(cell: Cell<T>) = finconCollector
            .within(cell.name.asSequence())
            .report(cell.value, cell.valueType)
        map.values.forEach { saveCell(it) }
    }

    fun restore(inconProvider: InconProvider) {
        fun <T> restoreCell(handle: CellHandle<T>) = with(this[handle]) {
            // If incon is missing, ignore it and move on
            inconProvider.within(name.asSequence()).provide<T>(valueType)?.let {
                map[handle] = copy(value=it)
            }
        }
        map.keys.forEach { restoreCell(it) }
    }

    fun stepBy(delta: Duration) {
        fun <T> stepCell(cell: Cell<T>) =
            cell.copy(value = cell.stepBy(cell.value, delta))
        map.replaceAll { _, state -> stepCell(state) }
    }
}

class BranchCellSet private constructor(
    private val trunk: CellSet,
    val edits: MutableMap<CellHandle<*>, BranchCell<*>>,
) : CellSet {
    constructor(trunk: CellSet) : this(trunk, mutableMapOf())

    data class BranchCell<T>(
        val cell: Cell<T>,
        val effect: Effect<T>,
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(cellHandle: CellHandle<T>): Cell<T> =
        (edits[cellHandle] as BranchCell<T>?)?.cell ?: trunk[cellHandle]

    @Suppress("UNCHECKED_CAST")
    override fun <T> emit(
        cellHandle: CellHandle<T>,
        effect: Effect<T>
    ) {
        edits.compute(cellHandle) { _, branchCell ->
            if (branchCell == null) {
                // If we've never written to this cell before,
                // pull the cell from the trunk and record the effect.
                BranchCell(trunk[cellHandle].applyEffect(effect), effect)
            } else {
                // If we have written to this cell before,
                // apply this effect on top of the effect already there.
                BranchCell(
                    (branchCell as BranchCell<T>).cell.applyEffect(effect),
                    branchCell.effect andThen effect,
                )
            }
        }
    }
}

private fun <T> Cell<T>.applyEffect(effect: Effect<T>) = (this as Cell<T>).copy(value = effect(value))
