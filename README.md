# Karzoun SyncLattice

[![CI](https://github.com/mkarson1997/karzoun-synclattice/actions/workflows/ci.yml/badge.svg)](https://github.com/mkarson1997/karzoun-synclattice/actions/workflows/ci.yml)
[![CodeQL](https://github.com/mkarson1997/karzoun-synclattice/actions/workflows/codeql.yml/badge.svg)](https://github.com/mkarson1997/karzoun-synclattice/actions/workflows/codeql.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=mkarson1997_karzoun-synclattice&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=mkarson1997_karzoun-synclattice)
[![Release](https://img.shields.io/github/v/release/mkarson1997/karzoun-synclattice)](https://github.com/mkarson1997/karzoun-synclattice/releases/latest)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

SyncLattice is a local-first replicated sync engine in Kotlin focused on deterministic CRDT convergence and durable offline state reconstruction.

## Engineering proof points

| Area | What the repository demonstrates |
| --- | --- |
| CRDT convergence | Deterministic LWW register and observed-remove set with remove-before-add tombstone correctness. |
| Causal reasoning | Vector clocks represent partial order while exact operation dots repair lower-sequence delivery holes. |
| Idempotency | Duplicate delivery is harmless when content matches; conflicting reuse of an operation identity is rejected. |
| Bounded reconciliation | Coroutine-based bidirectional synchronization transfers bounded batches of exact missing operations. |
| Durable local state | SQLite persists operation/context/tombstone data atomically before in-memory materialization. |
| Crash recovery | Persistent replicas reconstruct vector clock, CRDT state, known dots and local sequence by replay after restart. |
| Storage hardening | WAL, full synchronous mode, foreign keys, schema versioning and bounded busy timeout. |
| Convergence verification | Fixed-seed shuffled concurrent/duplicate replay plus explicit `A:3`-before-`A:2` gap-repair coverage. |
| Multi-runtime verification | JDK 21 and JDK 25 deterministic test matrix plus CodeQL and SonarQube Cloud. |
| Reproducible delivery | JAR, sources JAR, Maven POM, SHA-256 manifest, Maven package and build-provenance attestations. |
| Supply-chain hygiene | CI, CodeQL and release GitHub Actions are pinned to reviewed immutable commits. |

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

## Distribution

`v0.1.0` publishes the reviewed JVM artifacts through both GitHub Releases and GitHub Maven Packages:

- `karzoun-synclattice-0.1.0.jar`
- `karzoun-synclattice-0.1.0-sources.jar`
- generated Maven POM
- `SHA256SUMS.txt`
- GitHub build-provenance attestations for release JARs

The release workflow verifies artifact contents and checksums before publication. Future releases remain tag-driven.

## Important boundaries

SyncLattice does **not** claim consensus, linearizability, exactly-once delivery, crash-atomic coordination with external side effects, network transport, or production distributed-database semantics. SQLite durability is local to each replica. Authenticated transport and tombstone compaction are separate milestones.

## Build and test

Requires JDK 21+ and Gradle 9.1+.

```bash
gradle clean test
```

CI runs the deterministic core, shuffled convergence hardening, and file-backed SQLite restart suite on JDK 21 and JDK 25.

See [Architecture](docs/architecture.md), [Security](SECURITY.md), [Contributing](CONTRIBUTING.md), and the [Roadmap](ROADMAP.md).
