# Pyre - Architecture

Pyre is architected in concentric layers of functionality.
Each layer strictly depends on functionality of lower layers only.

Layers are allowed to reach more than one layer down.
We discourage the use of "pass-through" or "facade" classes, such that each layer connects only to the layer directly below it.
Such classes impose additional maintenance burden while providing little benefit, since the functionality at each layer is closely coupled anyways.

The currently developed layers, from most foundational to least, are:
- [utilities](utilities/README.md) - Generic programming utilities applicable to any Kotlin project.
- [ember](ember/README.md) - The lowest runnable layer, defining a minimal simulation.
- [spark](spark/README.md) - An "ergonomics" layer, wrapping ember in user-friendly utilities deemed essential to the success of Pyre.
- [flame](flame/README.md) - Advanced functionality, not essential but generally useful to at least some users of Pyre.
