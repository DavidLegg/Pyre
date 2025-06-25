package gov.nasa.jpl.pyre.examples.model_interfaces.lander

import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.time.Clocks
import gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.time.Time
import gov.nasa.jpl.pyre.flame.plans.ActivitySerializer
import gov.nasa.jpl.pyre.flame.plans.GroundedActivity
import gov.nasa.jpl.pyre.flame.plans.Model
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class Mission(context: SparkInitContext) : Model<Mission> {
    val config: Configuration
    val clocks: Clocks
    // val dataModel: DataModel
    // val dsnModel: DSNModel
    // val wakeModel: WakeModel
    // val commModel: CommModel
    // val powerModel: PowerModel
    // val engModel: EngModel
    // val HeatProbeModel: HeatProbeModel
    // val idsModel: IDSModel
    // val apssModel: APSSModel
    // val seisModel: SeisModel

    init {
        with (context) {
            config = Configuration()
            clocks = Clocks(context)
        }
    }

    override fun activitySerializer(): Serializer<GroundedActivity<Mission, *>> {
        return ActivitySerializer()
        // TODO: Add activities
    }
}