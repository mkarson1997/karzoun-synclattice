# Security Policy

## Supported versions

The project is pre-1.0. Security fixes target the latest released version and `main`.

## Reporting

Please report suspected vulnerabilities privately through GitHub's security reporting facilities when available rather than opening a public issue with exploit details.

## Current boundary

SyncLattice v0.1 core is an in-process convergence engine. It does not yet parse untrusted network input or provide authentication, authorization, encryption, durable storage, or a network service. Future transport and persistence milestones require explicit input bounds and threat modeling before release.
