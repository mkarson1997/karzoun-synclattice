# Karzoun SyncLattice

[![CI](https://github.com/mkarson1997/karzoun-synclattice/actions/workflows/ci.yml/badge.svg)](https://github.com/mkarson1997/karzoun-synclattice/actions/workflows/ci.yml)
[![CodeQL](https://github.com/mkarson1997/karzoun-synclattice/actions/workflows/codeql.yml/badge.svg)](https://github.com/mkarson1997/karzoun-synclattice/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

SyncLattice is a local-first replicated sync engine in Kotlin focused on deterministic CRDT convergence and durable offline state reconstruction.

## Convergence core

- immutable operation IDs as `(actor, sequence)` dots
- vector clocks with causal partial-order comparison and merge
- append-only operation log with duplicate rejection
- deterministic last-writer-wins register
- observed-remove set with tombstones that remain correct when remove arrives before add
- replica application that is idempotent under duplicate delivery
- coroutine-based bounded reconciliation between replicas
- exact-dot missing-operation discovery, avoiding vector-clock gap ambiguity
- fixed-seed shuffled replay tests covering mixed concurrent CRDT operations and duplicate delivery

## SQLite durability

`SQLiteOperationStore` persists the append-only operation history to a real file-backed SQLite database:

- versioned schema metadata
- normalized operation rows, vector-clock context rows, and OR-Set removed-dot rows
- one SQL transaction per complete operation append
- idempotent duplicate replay when the operation content is identical
- explicit conflict rejection if an existing `(actor, sequence)` ID is reused with different content
- deterministic reconstruction after process restart
- WAL journal mode, full synchronous durability, foreign keys, and a bounded busy timeout

`PersistentReplica` writes a new operation to SQLite before applying it to the in-memory CRDT materialization. On open, it reconstructs state by replaying the durable operation history. Persistent replicas implement the same bounded synchronization interface as in-memory replicas.

## Why exact dots for reconciliation?

A vector clock stores the greatest observed counter per actor. If operation `A:3` arrives before `A:2`, a clock value of `A=3` cannot prove that `A:2` was received. SyncLattice therefore uses exact operation IDs when deciding what a peer is missing. The test suite includes this delivery-hole case directly and verifies that bounded reconciliation still transfers the missing lower-sequence operation. Vector clocks remain useful for causal reasoning and conflict metadata.

## Important boundaries

SyncLattice does **not** claim consensus, linearizability, exactly-once delivery, crash-atomic coordination with external side effects, network transport, or production distributed-database semantics. SQLite durability is local to each replica. Authenticated transport and tombstone compaction are separate milestones.

## Build and test

Requires JDK 21+ and Gradle 9.1+.

```bash
gradle clean test
```

CI runs the deterministic core, shuffled convergence hardening, and file-backed SQLite restart suite on JDK 21 and JDK 25.

See [Architecture](docs/architecture.md), [Security](SECURITY.md), [Contributing](CONTRIBUTING.md), and the [Roadmap](ROADMAP.md).
