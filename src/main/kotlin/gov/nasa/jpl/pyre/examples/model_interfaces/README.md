# Model Interfaces

This example is both a test of Pyre and of an architecture for building and connecting multiple models.

## Motivation

We have an issue building integrated "digital-twin" style models.
Each subsystem (power, GNC, data, etc.) tends to have a bespoke model which handles that and only that concern.
Though they work well in isolation, integrating them together is difficult at best.
Ideally, we'd like them to conform to a common interface, in some way, to ease integration.
Such an interface needs to be flexible enough and simple enough that it doesn't impose any real restriction on subsystem models,
while still providing enough structure to make integration feasible and maintainable.

## Design

Each subsystem shall have one class, named `<subsystem>Model`.
That class shall take as input constants which configure the model,
and resources which give information from other models.
That class shall expose resources through getter methods or public properties
which comprise the outputs of the model.

This facilitates testing and development, since the "input" resources may be stubbed out with constants
or with resources which read values from a file.
This decouples the subsystem model from all other models.
Additionally, a subsystem team could write a `main` method for their model, which constructs the input resources
similarly to a test (constants or file reads), and runs just that one subsystem model.

So long as each model conforms to this pattern, an integration layer can be written which instantiates all subsystems
and wires all the inputs and outputs together.
Note that circular dependencies are handled trivially, with something like this:
```kotlin
val resourceB = resource(...)
val modelA = AModel(resourceB)
val modelB = BModel(modelA.resourceA)
forward(modelB.resourceB, resourceB)

// where we define forward like so, in the Pyre library
fun <A> forward(src: Resource<A>, dest: MutableResource<A>) {
    spawn("Forward", wheneverChanges(src) {
        dest.set(src.getDynamics())
    })
}
```
The integration layer is also a place to inject arbitrary "shim" code, smoothing the differences and disagreements between
the various models.
For example, if one model expects a discrete input, but another produces that information as a linear resource,
the integration layer can apply the discrete approximation to align them.

The integration layer can also choose among models dynamically.
Suppose, for example, that there's a "low-fidelity" GNC model, which does a simple trapezoidal approximation of all profiles,
and a "high-fidelity" GNC model, which simulates the turn in far more detail.
For short duration, high-precision planning, the high-fidelity model is required.
For long duration, coarse planning, the low-fidelity model is sufficient and far more performant.
Configuration parameters given to the integration layer could choose between the two,
and the integration layer is responsible for instantiating and connecting the appropriate model.
Something like this would suffice:
```kotlin
if (config.enableHighFidelityGnc) {
    val gncModel = HighFidelityGncModel(turnCommands, geometry.scPosition, ...)
    scAttitude = gncModel.spacecraftAttitude
    gncErrors = gncModel.errors
} else {
    val gncModel = LowFidelityGncModel(turnCommands)
    scAttitude = gncModel.attitude
    gncErrors = constant(emptyList())
}
```
Note that the two models need not conform to a common interface;
in the example above, they take different sets of input resources, and even provide slightly different output resources.
The integration layer can shim each model into compatibility.
The integration layer could even swap a model for a file-based "dummy" model, which simply plays back the results of a prior simulation.
This allows for "layered" simulation, where one simulates a subsystem like geometry early on, then replays those results
without resimulating them, safe in the knowledge that those resources don't depend on activities affecting other subsystems.
Such an approach has precedent in systems like APGen as a way to reduce computation.

Finally, this pattern of sub-models and integration layers could be recursive.
It may be the case that GNC decides to split their model into several sub-models, each independently written and tested.
The `GncModel` class may itself be an integration layer for a suite of sub-subsystem models.
From the system point of view, that's simply an implementation detail of `GncModel`.
