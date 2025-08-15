package gov.nasa.jpl.pyre.flame.units

object DoubleFieldScope : FieldScope<Double> {
    override val zero = 0.0
    override val one = 1.0
    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override fun Double.plus(other: Double): Double = this + other
    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override fun Double.minus(other: Double): Double = this - other
    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override fun Double.times(other: Double): Double = this * other
    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override fun Double.div(other: Double): Double = this / other
}