# Security Policy

## Supported versions

The project is pre-1.0. Security fixes target the latest released version and `main`.

## Reporting

Please report suspected vulnerabilities privately through GitHub's security reporting facilities when available rather than opening a public issue with exploit details.

## Current boundary

SyncLattice currently processes in-process operation objects and local SQLite files. The SQLite layer enables foreign keys, full synchronous durability, WAL journaling, and a bounded busy timeout, but it does not provide encryption at rest or protection from an attacker who can replace the database file.

There is no authentication, authorization, network encryption, or untrusted network decoder in the current milestone. Those controls belong to the future transport boundary and must be implemented before exposing synchronization to untrusted peers.
