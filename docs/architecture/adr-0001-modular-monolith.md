# ADR 0001: Use a Spring Modulith modular monolith

## Status

Accepted

## Context

Factweek needs clear domain ownership and testable boundaries but has no operational requirement for independently deployed services.

## Decision

Build one deployable Spring Boot application with Spring Modulith. Top-level business packages define application modules. Cross-module access is limited to public module APIs and, where asynchronous decoupling is valuable, application events.

Initial modules:

- `ingestion`: discovers source material and creates candidates.
- `technology`: owns reviewed technology facts and their lifecycle.
- `briefing`: selects published facts for a weekly reader-facing result.

## Consequences

- The architecture remains easy to run and debug locally.
- Module verification prevents accidental coupling.
- Modules can be extracted later if deployment or scaling evidence justifies it.
- Persistence is shared operationally, but tables remain owned by one module.
