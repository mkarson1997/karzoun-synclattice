# Karzoun SyncLattice v0.1.0

SyncLattice v0.1.0 is the first public release of the Kotlin local-first convergence engine and its SQLite durability layer.

## Proven behavior

- immutable `(actor, sequence)` operation identities
- vector clocks for causal partial-order reasoning
- exact-dot missing-operation discovery that does not confuse a higher observed counter with proof that lower operations arrived
- bounded coroutine reconciliation in both directions
- deterministic LWW register conflict resolution
- observed-remove set semantics with tombstones that survive remove-before-add delivery
- duplicate operation delivery is idempotent
- file-backed SQLite operation persistence with one transaction per complete operation
- conflicting reuse of an existing operation ID is rejected
- restart replay reconstructs register, set, vector clock, known dots, and local sequence
- persistent offline replicas reconcile and remain converged after both processes reopen
- JDK 21 and JDK 25 CI evidence
- 15 deterministic test cases, including 32 fixed shuffled duplicate-delivery runs inside the convergence hardening suite

## Important boundaries

SyncLattice v0.1.0 does **not** claim consensus, linearizability, exactly-once delivery, globally ordered database semantics, crash-atomic coordination with external side effects, authenticated network transport, encryption at rest, or production distributed-database guarantees.

The current synchronization interface is in-process. SQLite durability is local to each replica. Authenticated transport and tombstone compaction remain future milestones.

## Distribution

The GitHub Release contains the JVM JAR, sources JAR, generated Maven POM, and `SHA256SUMS.txt`. The same reviewed JAR/POM/sources are deployed as `com.karzoun:karzoun-synclattice:0.1.0` to GitHub Maven Packages. Release JARs receive GitHub build-provenance attestations.
