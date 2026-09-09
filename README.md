# Karzoun SyncLattice

[![CI](https://github.com/mkarson1997/karzoun-synclattice/actions/workflows/ci.yml/badge.svg)](https://github.com/mkarson1997/karzoun-synclattice/actions/workflows/ci.yml)
[![CodeQL](https://github.com/mkarson1997/karzoun-synclattice/actions/workflows/codeql.yml/badge.svg)](https://github.com/mkarson1997/karzoun-synclattice/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

SyncLattice is a local-first replicated sync engine in Kotlin. The first milestone focuses on deterministic CRDT convergence before adding durable storage or network transport.

## v0.1 core

- immutable operation IDs as `(actor, sequence)` dots
- vector clocks with causal partial-order comparison and merge
- append-only operation log with duplicate rejection
- deterministic last-writer-wins register
- observed-remove set with tombstones that remain correct when remove arrives before add
- replica application that is idempotent under duplicate delivery
- coroutine-based bounded reconciliation between replicas
- exact-dot missing-operation discovery, avoiding vector-clock gap ambiguity
- deterministic convergence tests under opposite delivery orders and offline divergence

## Why exact dots for reconciliation?

A vector clock stores the greatest observed counter per actor. If operation `A:3` arrives before `A:2`, a clock value of `A=3` cannot prove that `A:2` was received. SyncLattice therefore uses exact operation IDs when deciding what a peer is missing. Vector clocks remain useful for causal reasoning and conflict metadata.

## Important boundaries

This milestone does **not** claim consensus, linearizability, exactly-once delivery, durable storage, network transport, or production distributed-database semantics. SQLite persistence and transport are separate milestones.

## Build and test

Requires JDK 21+ and Gradle 9.1+.

```bash
gradle clean test
```

CI runs the same deterministic suite on JDK 21 and JDK 25.

See [Architecture](docs/architecture.md), [Security](SECURITY.md), [Contributing](CONTRIBUTING.md), and the [Roadmap](ROADMAP.md).
