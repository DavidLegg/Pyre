package gov.nasa.jpl.pyre.examples.lander

import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.examples.lander.models.data.DataModel
import gov.nasa.jpl.pyre.examples.lander.models.dsn.DSNModel
import gov.nasa.jpl.pyre.examples.lander.models.time.Clocks
import gov.nasa.jpl.pyre.examples.lander.models.wake.WakeModel
import gov.nasa.jpl.pyre.flame.composition.subContext
import gov.nasa.jpl.pyre.flame.plans.ActivitySerializer
import gov.nasa.jpl.pyre.flame.plans.GroundedActivity
import gov.nasa.jpl.pyre.flame.plans.Model
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class Mission(context: SparkInitContext) : Model<Mission> {
    val config: Configuration
    val clocks: Clocks
    val dataModel: DataModel
    val dsnModel: DSNModel
    val wakeModel: WakeModel
    // val commModel: CommModel
    // val powerModel: PowerModel
    // val engModel: EngModel
    // val HeatProbeModel: HeatProbeModel
    // val idsModel: IDSModel
    // val apssModel: APSSModel
    // val seisModel: SeisModel

    init {
        // TODO: Consider how to load configuration from environment with each run?
        //   Is this something that even makes sense in Pyre?
        with (context) {
            config = Configuration()
            clocks = Clocks(subContext("time"))
            dataModel = DataModel(subContext("data"))
            dsnModel = DSNModel(subContext("dsn"))
            wakeModel = WakeModel(subContext("wake"))
        }

    }

    override fun activitySerializer(): Serializer<GroundedActivity<Mission, *>> {
        return ActivitySerializer()
        // TODO: Add activities
    }
}