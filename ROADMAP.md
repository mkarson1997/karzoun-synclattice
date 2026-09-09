# Roadmap

## v0.1 foundation

- [x] vector clocks and immutable operation dots
- [x] append-only in-memory operation log
- [x] deterministic LWW register
- [x] observed-remove set with remove-before-add tombstones
- [x] idempotent replica application
- [x] bounded coroutine reconciliation
- [x] deterministic convergence tests
- [x] SQLite append-only durable operation log
- [x] file-backed crash/restart reconstruction tests
- [x] duplicate-ID payload/context conflict rejection

## Next

- [ ] bounded serialization format with schema versioning for peer transport
- [ ] authenticated transport milestone
- [ ] explicit replay-window and message-size limits
- [ ] compaction and tombstone lifecycle design with convergence proof/tests
- [ ] measured benchmarks only after stable transport paths

No consensus, linearizability, exactly-once, or globally ordered database claim is planned for v0.1.
