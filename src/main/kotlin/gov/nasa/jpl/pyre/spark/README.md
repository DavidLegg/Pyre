# Pyre - Spark

Spark is the principal "ergonomics" layer of Pyre, building on top of ember.
It's meant to contain functionality which is essential to the real-world use of Pyre.

When deciding whether some new feature belongs in this layer, we ask:
1. Can an equivalent simulation be written without this feature, even if doing so would be painful?
    - If not, this feature changes the fundamental capability of Pyre, and belongs in ember instead.
2. Are most modelers using Pyre expected to use this feature?
    - If not, this feature is not central / essential enough to go in spark. It belongs in a higher level.
