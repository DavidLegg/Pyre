package gov.nasa.jpl.pyre.general.units

// These structures indicate what operations are permitted on a generic type T, as interests unit-aware calculation.
// Implement the strongest structure your type permits.
// Operations consistent with that type will be available on unit-aware types
// whenever the appropriate algebraic structure is present in the context parameters.

interface Scaling<T> {
    operator fun Double.times(other: T): T
}

interface VectorSpace<T> : Scaling<T> {
    val zero: T
    operator fun T.plus(other: T): T
    operator fun T.minus(other: T): T
}

interface Ring<T> : VectorSpace<T> {
    val one: T
    operator fun T.times(other: T): T
}

interface Field<T> : Ring<T> {
    operator fun T.div(other: T): T
}