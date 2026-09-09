# Architecture

## Core model

Each mutation has an immutable `Dot(actor, sequence)` identity and a vector-clock context. Replicas keep an append-only operation log keyed by exact dots and materialize two CRDTs: a deterministic LWW register and an observed-remove set.

## Convergence

The register winner is chosen by `(logicalTime, actor, sequence)`, making delivery order irrelevant. OR-Set removes record the add dots observed at removal time. Tombstones are retained so a removed add cannot reappear if that add arrives after its remove.

## Reconciliation

Peers exchange exact known-dot sets and request bounded batches of missing operations. Vector clocks are intentionally not used as the sole missing-operation detector because a max counter cannot represent delivery holes. Reconciliation fetches both directions concurrently with Kotlin coroutines, then applies each bounded batch idempotently.

## Current trust boundary

The core accepts in-process `Operation` objects. There is no untrusted wire decoder, persistence layer, authentication, encryption, or network protocol in this milestone. Those concerns are separate work and must not be inferred from the current API.
