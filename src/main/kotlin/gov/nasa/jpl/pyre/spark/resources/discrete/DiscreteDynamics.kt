package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.DynamicsMonad
import gov.nasa.jpl.pyre.spark.resources.FullDynamics

data class Discrete<A>(val value: A) : Dynamics<A, Discrete<A>> {
    override fun value() = value
    override fun step(t: Duration) = this
}

object DiscreteMonad {
    fun <A> pure(a: A): Discrete<A> = Discrete(a)
    fun <A, B> apply(a: Discrete<A>, f: Discrete<(A) -> B>): Discrete<B> = Discrete(f.value(a.value))
    fun <A> join(a: Discrete<Discrete<A>>): Discrete<A> = a.value
    // TODO: Generate other methods
    fun <A, B> apply(f: Discrete<(A) -> B>): (Discrete<A>) -> Discrete<B> = { apply(it, f) }
    fun <A, B> map(a: Discrete<A>, f: (A) -> B): Discrete<B> = apply(a, pure(f))
    fun <A, B> bind(a: Discrete<A>, f: (A) -> Discrete<B>): Discrete<B> = join(map(a, f))
}

object DiscreteDynamicsMonad {
    fun <A> pure(a: A): FullDynamics<Discrete<A>> = DynamicsMonad.pure(DiscreteMonad.pure(a))
    fun <A, B> apply(a: FullDynamics<Discrete<A>>, f: FullDynamics<Discrete<(A) -> B>>): FullDynamics<Discrete<B>> =
        DynamicsMonad.apply(a, DynamicsMonad.map(f, DiscreteMonad::apply))
    fun <A> distribute(a: Discrete<FullDynamics<A>>): FullDynamics<Discrete<A>> =
        DynamicsMonad.map(a.value, DiscreteMonad::pure)
    fun <A> join(a: FullDynamics<Discrete<FullDynamics<Discrete<A>>>>): FullDynamics<Discrete<A>> =
        DynamicsMonad.map(DynamicsMonad.join(DynamicsMonad.map(a, DiscreteDynamicsMonad::distribute)), DiscreteMonad::join)
    // TODO: Generate other methods
    fun <A, B> apply(f: FullDynamics<Discrete<(A) -> B>>): (FullDynamics<Discrete<A>>) -> FullDynamics<Discrete<B>> = { apply(it, f) }
    fun <A, B> map(a: FullDynamics<Discrete<A>>, f: (A) -> B): FullDynamics<Discrete<B>> = apply(a, pure(f))
    fun <A, B> bind(a: FullDynamics<Discrete<A>>, f: (A) -> FullDynamics<Discrete<B>>): FullDynamics<Discrete<B>> = join(map(a, f))
}
