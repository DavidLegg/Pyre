package gov.nasa.jpl.pyre.examples.lander.models.ids

import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.EnumResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import kotlin.math.pow

class IDSModel(
    context: SparkInitContext
) {

    enum class IDAMode {
        Idle,
        Moving,
        Grappling
    }

    val idaMode: MutableDiscreteResource<IDAMode>
    val idaSurvivalHeatersNominal: BooleanResource

    init {
        with (context) {
            idaMode = registeredDiscreteResource("IDAMode", IDAMode.Idle)
            idaSurvivalHeatersNominal = registeredDiscreteResource("IDASurvivalHeatersNominal", true)
        }
    }

    fun computeSize(compQuality: Int): Double {
        val a = 30226.95970160277
        val b = -0.06658743610336151
        val c = -1459.444507542347
        val d = 0.002534592140626373
        val e = 80.74971322012104
        val f = -4.635963037609322E-05
        val g = -1.35191059095006
        val h = 3.855556487825687E-07
        val i = 0.006743543630872695
        val j = -1.187301787888751E-09
        val k = 0
        val l = 1
        val m = 1200.0
        val n = 1648.0
        val o = 0
        val x = compQuality
        val x2 = compQuality.toDouble().pow(2.0).toInt()
        val x3 = compQuality.toDouble().pow(3.0).toInt()
        val x4 = compQuality.toDouble().pow(4.0).toInt()
        val x5 = compQuality.toDouble().pow(5.0).toInt()

        val image_size: Double = if (compQuality == 0) {
            (1024 * 1024 * 16).toDouble()
        } else {
            (((a + c * x + e * x2 + g * x3 + i * x4) / (1 + b * x + d * x2 + f * x3 + h * x4 + j * x5)) * (k + (l * 1024 * 1024) / (m * n)) + o) * 8.0
        }
        return image_size / 1.0e6
    }
}