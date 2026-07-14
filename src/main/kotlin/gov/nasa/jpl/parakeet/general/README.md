# Pyre - General

General is the layer of pyre responsible for the least critical components.
A modeler could reasonably be expected to build a simulation with relative ease without the components in this layer.
Nonetheless, classes in this layer demonstrate useful patterns, with an eye towards how Pyre can and should be used in practice.

If a new feature can be placed in this layer, and there's no particularly compelling reason to put that feature in a lower layer,
then that feature almost certainly should be placed in this layer.
Put another way, this layer is the default location for new features, and we need a compelling reason to go against this default.
