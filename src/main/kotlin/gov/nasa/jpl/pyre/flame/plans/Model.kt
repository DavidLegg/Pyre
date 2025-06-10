package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.ember.Serializer

interface Model<M : Model<M>> {
    fun activitySerializer(): Serializer<GroundedActivity<M, *>>
}