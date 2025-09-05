# GNC Model

The GNC model provides a good example of a "typical" subsystem model, in a number of ways.

## Input Resources

It accepts resources, notionally coming from other subsystems, as inputs.
We collect these into a single Inputs data class, given to the constructor, to
1. make it clear what the interface "into" this subsystem is, and
2. make it easy to "stub out" that interface later.

In particular, we can build a stub Inputs data class where resources follow a fixed profile.
This could replicate a scenario from production produced by a full-system simulation, but do so in isolation.
We simply record the profile from production on all the input resources, then "replay" those recorded profiles as inputs to this subystem.
This subsystem can then run on its own, which will be faster and cleaner to study and test.

Note that this requires some amount of discipline.
It's often easier to just pass one model to another, since they have all the resources you need.
Inevitably, doing this means you'll wind up calling methods on one model from another model.
At that point, you've deeply linked the behavior of the two models, in a way that's hard to extract.
It's hard to write a test that spins up only one of these models, for example.
If this becomes standard practice, you'll wind up with a web of these ties, such that it's basically impossible to run
anything except a full-system simulation.

## Public Mutable Resources

There are a number of states "owned" by the GNC subsystem, which can be changed atomically by actors outside the GNC subsystem.
By "atomic", I mean that an outside actor could change these states in any way they want, and without changing other GNC states
in a coordinated way, and the result would be a sensible GNC subsystem state.
The pointing target resources are a good example of this - any pointing target is allowed,
and nothing else needs to change "in sync" with the pointing targets.
The GNC subsystem may react to this change, e.g. by initiating a turn, but that behavior is in response to a new, valid state.

States which fit this description can be exposed as `MutableResource`s, rather than writing model methods to update their values.
This gives an outside actor maximum flexibility in how to interact with this state, with no boilerplate code.

## Private Mutable Resources

By contrast with public mutable states, some mutable states in the GNC subsystem are private.
These states are either computed by the GNC system, like the spacecraft attitude,
or need to change in a coordinated way.

Suppose the GNC system (as many in real life do) had a notion of "current" and "next" pointing target,
such that we could update the next pointing target first and execute the turn with a second, separate command.
We might model this with mutable resources holding the current target, next target, and whether we are turning.
The next target could be exposed as a public mutable resource, updated freely by outside actors.
The current target and whether we are turning would likely be private, since executing a turn would require
both updating the current target and setting the mode to "turning".

Often, these private mutable states are publicly exposed as (immutable) resources.
This is as simple as adding a public field with the immutable resource type, which "gets" the private field.

## Derived Resources

Like private mutable resources, derived resources are a consequence of other states in the subsystem,
and can't be directly modified by actors outside this subsystem.
Unlike private mutable resources, derived resources are strictly a function of the current value of other resources.
For example, `targetAttitude` is derived from the chosen pointing targets and body axes,
and the inputs correlating pointing targets to J2000 vectors.

Since derived resources _cannot_ have effects on them, they _always_ equal the formula they're defined with.
Derived resources are in some sense "declarative" - you give the formula defining `targetAttitude`, not a procedure for updating it.
This is highly valuable for reasoning about a model.
Derived resource state, often in a single line, the entirety of their behavior.

### Performance

Derived resources are "transparent" - when a derived resource read, it reads its inputs and computes its value on the fly.
Derived resources do not store their own value.[^derived-store-value]
In most cases, the derivation formula is nearly trivial to compute, so re-computing whenever the value is read is very fast.

[^derived-store-value]: There are a few library methods that produce something which looks like a derived resource, but in fact stores a value.
    The most common is probably the `integrate` method, which is logically a derived value, but internally uses a mutable resource
    updated by a daemon task, spawned within the library method.
    Under the terminology of this document, such library methods are closer to a small model in their own right,
    with an input resource, private mutable resource, and public immutable view of that resource.
    These are the exception, and only usually come up when the history of the input matters, not just its current value.

In some cases, the derivation formula is expensive, or the resource is read excessively often compared to the frequency of input changes.
In these cases, there are ways to cache the computation.
This trades the overhead of running a task to keep the cache up-to-date
against the savings of computing the derived value once each time an input changes,
instead of computing it every time the derived resource is read.

It's hard to predict when this trade is worthwhile.
In my experience, the best approach is to use the default derived resources everywhere you can, as a first draft.
If performance is an issue, use profiling to determine which resources may benefit from caching,
then check whether caching actually improves performance, one resource at a time.

## Unit Awareness

The GNC model makes extensive use of unit-aware states, and freely mixes them with unit-free states.
For example, the turn simulation uses unit-aware computations and a variety of angle and time units,
while resources like modes and rotations don't have units.
We even see examples like the `pointingTargetError`, where a unit-free type like `Rotation` reports an answer as a `Double`,
and we can apply the unit given in the documentation to the resulting resource.

Note also that, for efficiency, units should be applied primarily to resources, not the values in them.
When units are applied to a resource, and resource-level derivations are written, dimension checks are done once when initializing.
Conversion factors are also computed once, then "baked in" to the resource derivation as primitive scaling operations.

By contrast, if units are applied to every value separately, dimension checking and conversion factors are recomputed with
every data point, which can be a significant performance burden.

[//]: # (TODO: Subsystem Activities)
