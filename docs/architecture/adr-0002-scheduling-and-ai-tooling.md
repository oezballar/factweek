# ADR 0002: Use Spring Scheduling and Spring AI; defer external workflow tooling

## Status

Accepted

## Context

Factweek imports unverified source candidates and will later extract structured fact proposals from reproducible source content. These activities need a clear operational ownership model without weakening the modular-monolith boundaries or introducing workflow infrastructure before there is evidence that it is needed.

## Decision

### Spring Scheduling

Scheduled GDELT imports will be implemented with Spring Scheduling after the manual GDELT import has been verified as stable through automated tests and reproducible manual runs. The manual import endpoint remains available for development and operations. The schedule's cron expression, time zone, and activation must be configurable.

Scheduling belongs to the `ingestion` module because it triggers ingestion work. While Factweek has one application instance, no distributed lock is required. Before horizontal scaling, the team must evaluate a database-backed lock or a solution such as ShedLock.

### Spring AI

Spring AI will be used for the first structured extraction of fact proposals. The integration will sit behind an application-owned port. LLM output must be typed, validated, and evaluated; it must never be published directly. Provider-specific classes must not enter the domain model.

### Embabel

Embabel is not required for the first extraction flow. It may be reconsidered only when multi-step research, conflict resolution, or agentic RAG is actually needed, and only if it shows a measurable advantage over a simpler Spring AI flow against the Golden Dataset.

### n8n and Flowise

n8n and Flowise are not part of the planned Factweek architecture. Scheduling, domain logic, and AI flows remain versioned and tested in the Spring project. Additional operational surfaces and distributed workflow definitions would currently add more complexity than value.

## Decision drivers

- Keep scheduling and ingestion ownership inside the module that owns candidate acquisition.
- Preserve a manually operable, testable path before automating it.
- Keep AI integration replaceable and prevent provider concerns from leaking into the domain.
- Prefer simple, versioned application code until a measured need justifies more workflow or agent infrastructure.
- Avoid distributed-locking complexity until deployment topology requires it.

## Consequences

- The manual GDELT import remains the operational fallback and the basis for scheduler verification.
- A scheduler implementation must expose configuration for activation, cron expression, and time zone.
- A multi-instance deployment cannot enable scheduled imports until locking has been evaluated and implemented.
- Fact proposal extraction will require typed contracts, deterministic validation, and evaluation against a Golden Dataset.
- Embabel, n8n, and Flowise do not add dependencies or runtime components at this stage.

## Conditions for reconsideration

- Horizontal scaling or multiple application instances require a reviewed distributed-locking decision before scheduling is enabled.
- Embabel can be reconsidered when a Golden-Dataset evaluation demonstrates a measurable benefit for genuine multi-step or agentic work.
- n8n or Flowise can be reconsidered only for a concrete external integration or no-code use case whose benefits outweigh the added operational and governance cost.
- Spring AI provider or port choices can be revisited when evaluation, cost, reliability, or capability evidence warrants it.
