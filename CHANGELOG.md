# Changelog

All notable changes to SyncLattice are documented here.

## [0.1.0] - 2026-09-09

### Added

- Kotlin/JVM 21 CRDT convergence core with JDK 21 and JDK 25 CI coverage.
- Immutable actor/sequence operation dots and vector-clock causal comparison.
- Exact-dot append-only operation logging and bounded coroutine reconciliation.
- Deterministic LWW register and observed-remove set with remove-before-add tombstones.
- SQLite file-backed durable operation log with versioned normalized schema.
- Transactional durable append, idempotent duplicate replay, and conflicting-dot rejection.
- Persistent replica restart reconstruction and persistent offline reconciliation.
- Deterministic delivery-hole proof and 32 fixed shuffled duplicate-delivery convergence runs.
- CodeQL Java/Kotlin security analysis and Dependabot configuration.
