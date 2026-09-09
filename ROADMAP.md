# Roadmap

## v0.1 core

- [x] vector clocks and immutable operation dots
- [x] append-only in-memory operation log
- [x] deterministic LWW register
- [x] observed-remove set with remove-before-add tombstones
- [x] idempotent replica application
- [x] bounded coroutine reconciliation
- [x] deterministic convergence tests

## Next

- [ ] SQLite append-only durable operation log
- [ ] crash/restart reconstruction tests
- [ ] bounded serialization format with schema versioning
- [ ] authenticated transport milestone
- [ ] compaction and tombstone lifecycle design with convergence proof/tests
- [ ] measured benchmarks only after stable persistence and transport paths

No consensus, linearizability, exactly-once, or globally ordered database claim is planned for v0.1.
