# Architecture

## Core model

Each mutation has an immutable `Dot(actor, sequence)` identity and a vector-clock context. Replicas keep an append-only operation log keyed by exact dots and materialize two CRDTs: a deterministic LWW register and an observed-remove set.

## Convergence

The register winner is chosen by `(logicalTime, actor, sequence)`, making delivery order irrelevant. OR-Set removes record the add dots observed at removal time. Tombstones are retained so a removed add cannot reappear if that add arrives after its remove.

## Reconciliation

Peers exchange exact known-dot sets and request bounded batches of missing operations. Vector clocks are intentionally not used as the sole missing-operation detector because a max counter cannot represent delivery holes. Reconciliation fetches both directions concurrently with Kotlin coroutines, then applies each bounded batch idempotently.

Both `Replica` and `PersistentReplica` implement the same `SyncPeer` contract.

## Durable local operation log

`SQLiteOperationStore` uses a normalized schema with three logical pieces: the operation row, zero or more vector-clock context rows, and zero or more OR-Set removed-dot rows. One operation is appended in one SQLite transaction. Reusing an existing dot with identical content is an idempotent replay; reusing it with different content is rejected.

`PersistentReplica` follows a durable-first mutation order: append to SQLite, then apply to the in-memory materialization. A crash after the commit but before materialization can therefore be recovered by replay on restart. This is not a transaction with any external application side effect.

## Current trust boundary

The engine accepts in-process `Operation` objects and local SQLite paths. There is no untrusted wire decoder, authentication, encryption, or network protocol yet. Future transport work requires explicit message bounds, authentication, replay handling, and threat modeling before release.
