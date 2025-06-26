package gov.nasa.jpl.pyre.flame.resources.polynomial

import gov.nasa.jpl.pyre.flame.resources.polynomial.Polynomial.Companion.polynomial
import gov.nasa.jpl.pyre.spark.reporting.register
import gov.nasa.jpl.pyre.spark.resources.DynamicsMonad
import gov.nasa.jpl.pyre.spark.resources.ResourceMonad
import gov.nasa.jpl.pyre.spark.resources.ResourceMonad.bind
import gov.nasa.jpl.pyre.spark.resources.ResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.ResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.ThinResourceMonad
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.Discrete
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.emit
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.sparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.whenever

object PolynomialResourceOperations {
    context(SparkInitContext)
    fun polynomialResource(name: String, vararg coefficients: Double): MutablePolynomialResource =
        resource(name, polynomial(*coefficients), Polynomial.serializer())

    fun constant(value: Double): PolynomialResource = pure(polynomial(value))

    fun SparkInitContext.register(name: String, resource: PolynomialResource) = register(name, resource, Polynomial.serializer())

    fun SparkInitContext.registeredPolynomialResource(name: String, vararg coefficients: Double) =
        polynomialResource(name, *coefficients).also { register(name, it) }

    fun DiscreteResource<Double>.asPolynomial(): PolynomialResource = map(this) { polynomial(it.value) }

    fun PolynomialResource.derivative(): PolynomialResource = map(this, Polynomial::derivative)

    context(SparkInitContext)
    fun PolynomialResource.integral(name: String, startingValue: Double): IntegralResource {
        val integral = polynomialResource(name, startingValue)
        spawn("Update $name", whenever(map(this, integral) {
            p, q -> Discrete(p.integral(q.value()) != q)
        }) {
            with (sparkTaskScope()) {
                val integrandDynamics = this@PolynomialResource.getDynamics()
                integral.emit { q -> DynamicsMonad.map(integrandDynamics) { it.integral(q.data.value()) } }
            }
        })
        return object : IntegralResource, PolynomialResource by integral {
            context(SparkTaskScope<*>)
            override suspend fun increase(amount: Double) = integral.increase(amount)
        }
    }

    context(SparkInitContext)
    fun PolynomialResource.registeredIntegral(name: String, startingValue: Double) =
        integral(name, startingValue).also { register(name, it) }

    infix fun PolynomialResource.greaterThan(other: PolynomialResource): BooleanResource =
        bind(this, other) { p, q -> ThinResourceMonad.pure(p greaterThan q) }
    infix fun PolynomialResource.greaterThanOrEquals(other: PolynomialResource): BooleanResource =
        bind(this, other) { p, q -> ThinResourceMonad.pure(p greaterThanOrEquals q) }
    infix fun PolynomialResource.lessThan(other: PolynomialResource): BooleanResource =
        bind(this, other) { p, q -> ThinResourceMonad.pure(p lessThan q) }
    infix fun PolynomialResource.lessThanOrEquals(other: PolynomialResource): BooleanResource =
        bind(this, other) { p, q -> ThinResourceMonad.pure(p lessThanOrEquals q) }

    infix fun PolynomialResource.greaterThan(other: Double) = this greaterThan constant(other)
    infix fun PolynomialResource.greaterThanOrEquals(other: Double) = this greaterThanOrEquals constant(other)
    infix fun PolynomialResource.lessThan(other: Double) = this lessThan constant(other)
    infix fun PolynomialResource.lessThanOrEquals(other: Double) = this lessThanOrEquals constant(other)

    context(SparkTaskScope<*>)
    suspend fun MutablePolynomialResource.increase(amount: Double) = this.emit { p: Polynomial -> p + polynomial(amount) }
    context(SparkTaskScope<*>)
    suspend fun MutablePolynomialResource.decrease(amount: Double) = increase(-amount)

    context(SparkTaskScope<*>)
    suspend fun IntegralResource.decrease(amount: Double) = increase(-amount)
}

/**
 * A restriction of a full [MutablePolynomialResource] which supports only increase/decrease operations.
 * This can be used to represent a quantity with both discrete and continuous changes.
 * Integrating a rate resource accounts for continuous change, and increase/decrease operations on the result
 * account for discrete changes.
 */
interface IntegralResource : PolynomialResource {
    context(SparkTaskScope<*>)
    suspend fun increase(amount: Double)
}
