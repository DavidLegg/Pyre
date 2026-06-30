package gov.nasa.jpl.pyre.general.resources.discrete

import gov.nasa.jpl.pyre.foundation.resources.Expiring
import gov.nasa.jpl.pyre.foundation.resources.ThinResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.Discrete
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import kotlin.time.Duration

object DiscreteDerivedResources {
    /**
     * Derive a [DiscreteResource] from other discrete resources.
     *
     * The resulting resource will correctly compute its expiry from the sources it's derived from.
     */
    fun <T> discreteDerivedResource(block: context (DiscreteDerivedResourceScope) () -> T): DiscreteResource<T> {
        return ThinResource {
            var expiry = Duration.INFINITE
            val resultValue = block(object : DiscreteDerivedResourceScope {
                override fun <V> getValue(resource: DiscreteResource<V>): V {
                    // This getDynamics call is implicitly using the ResourceScope of the returned resource.
                    val dynamics = resource.getDynamics()
                    // We'll implicitly fold in the expiry information
                    expiry = minOf(expiry, dynamics.expiry)
                    return dynamics.data.value
                }
            })
            // Having sampled all our source resources and computed our expiry safely, return the result.
            Expiring(Discrete(resultValue), expiry)
        }
    }

    interface DiscreteDerivedResourceScope {
        fun <V> getValue(resource: DiscreteResource<V>): V
    }

    context (scope: DiscreteDerivedResourceScope)
    fun <V> DiscreteResource<V>.getValue(): V = scope.getValue(this)
}