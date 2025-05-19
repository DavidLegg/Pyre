package org.example.gov.nasa.jpl.pyre.state

import gov.nasa.jpl.pyre.state.Cell
import org.example.gov.nasa.jpl.pyre.io.Serializer

@Suppress("UNCHECKED_CAST")
class CellSet {
    class CellHandle<T, E>(val name: String, val serializer: Serializer<T>)
    data class CellState<T, E>(val cell: Cell<T, E>, val effect: E)

    private val map: MutableMap<CellHandle<*, *>, CellState<*, *>>

    public constructor() {
        this.map = mutableMapOf()
    }

    private constructor(map: MutableMap<CellHandle<*, *>, CellState<*, *>>) {
        this.map = map
    }

    fun <T: Any, E> allocate(cell: Cell<T, E>) =
        CellHandle<T, E>(cell.name, cell.serializer)
            .also { map[it] = CellState(cell, cell.effectTrait.empty()) }

    operator fun <T, E> get(cellHandle: CellHandle<T, E>): Cell<T, E> {
        val cellState = map[cellHandle] as CellState<T, E>
        val cell = cellState.cell
        return cell.copy(value = cell.applyEffect(cell.value, cellState.effect))
    }

    fun <T, E> emit(cellHandle: CellHandle<T, E>, effect: E) =
        map.compute(cellHandle) { _, cellState -> (cellState as CellState<T, E>)
            .copy(effect = cellState.cell.effectTrait.sequential(cellState.effect, effect)) }

    fun split(): CellSet {
        fun <T, E> collapseCellState(cs: CellState<T, E>) = CellState(
            cs.cell.copy(value=cs.cell.applyEffect(cs.cell.value, cs.effect)),
            cs.cell.effectTrait.empty())
        return CellSet(map.mapValuesTo(mutableMapOf()) { (_, cellState) -> collapseCellState(cellState) })
    }

    companion object {
        fun join(cellSets: Sequence<CellSet>): CellSet {
            val mergedMap: MutableMap<CellHandle<*, *>, CellState<*, *>> = mutableMapOf()
            for (cs in cellSets) {
                cs.map.forEach { (handle, state) -> mergedMap.merge(handle, state) { s1, s2 -> join(s1, s2) } }
            }
            return CellSet(mergedMap)
        }

        private fun <T, E> join(s1: CellState<T, E>, s2: CellState<*, *>): CellState<T, E> {
            // Just a sanity check - this could be omitted for performance...
            require(s1.cell.value == s2.cell.value)
            return s1.copy(effect = s1.cell.effectTrait.concurrent(s1.effect, s2.effect as E))
        }
    }
}