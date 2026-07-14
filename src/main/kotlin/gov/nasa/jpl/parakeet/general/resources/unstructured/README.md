# Unstructured Resources

Pyre's resource system is very flexible.
Most modeler's needs are well-covered by discrete, polynomial, and timer/clock resources.
However, there are cases where either
1. None of these types of resources are well-suited to the problem at hand, or
2. A consumer of some resource need not care how the resource changes.

For these cases, the "unstructured" resource may be appropriate.
Unstructured dynamics impose no restriction on how their value evolves over time.
One can only sample the value of unstructured dynamics at any point in time.

By contrast, discrete and polynomial dynamics (for example) expose the "structure" of their value's evolution,
such that one can reason about the value over an interval.
For example, it's reasonable to ask "Does this polynomial ever exceed this threshold?", while it would be unreasonable
to ask that about an unstructured dynamics.

Any dynamics can be viewed as Unstructured using `asUnstructured`, but the converse is false.
Instead, one can approximate an unstructured resource as another kind, explicitly acknowledging the error of doing so.
