# Factweek project plan

This is a compact living plan. It records the current delivery state and the order in which remaining work should be approached.

## Product goal

Deliver a personalized weekly briefing of relevant, evidenced technology changes without turning unverified source material or model output into facts.

## Product principles

- Source material is not a fact.
- Deterministic domain rules remain ordinary, testable code.
- LLM output is typed, validated, evaluated, and never published directly.
- Module ownership and operational simplicity take precedence over premature infrastructure.

## Scope of v0.1

- Discover and persist technology candidates from GDELT.
- Retrieve reproducible source content for stored candidates.
- Extract, assess, and review structured fact proposals.
- Produce a weekly briefing from reviewed facts.
- Establish a Golden Dataset and a deployable portfolio release.

## Non-goals

- Direct publication of candidates or LLM output.
- Scheduler implementation before manual imports are stable.
- Agentic workflow infrastructure, n8n, and Flowise.
- Spring AI, Embabel, or scheduling dependencies before their respective implementation phases.

## Architecture modules

| Module | Responsibility |
| --- | --- |
| `ingestion` | Discover candidates, retrieve source content, and coordinate ingestion work. |
| `technology` | Own reviewed technology facts and their lifecycle. |
| `briefing` | Select reviewed facts for the weekly reader-facing briefing. |

## Current status

- Product specification: complete.
- Fact schema: complete.
- Spring Modulith baseline: complete.
- PostgreSQL, Flyway, and Testcontainers: complete.
- Candidate persistence: complete.
- Concurrency-safe deduplication: complete.
- Manual GDELT import: complete (merged into local `main`).
- Structured fact extraction: next planned capability, after source-content retrieval.
- Human review workflow: planned.
- Weekly briefing from reviewed facts: basic structure exists; the complete vertical slice is planned.
- Golden Dataset and evaluation: planned.
- Portfolio release and deployment: planned.

## Roadmap

1. Discover and store GDELT candidates.
2. Retrieve source content.
3. Extract structured fact proposals with Spring AI.
4. Assess relevance, evidence, and duplicates.
5. Enable human review.
6. Generate the weekly briefing.
7. Build the Golden Dataset and evaluation.
8. Create the portfolio release.

## Quality metrics

- Import success and failure rate, candidate discovery count, and deduplication rate.
- Source-content retrieval completeness and reproducibility.
- Fact-proposal schema validity and evidence coverage.
- Golden-Dataset extraction quality, including precision, recall, and review agreement.
- Time from candidate discovery to reviewed briefing inclusion.

## Technology decisions

- Spring Modulith modular monolith: [ADR 0001](../architecture/adr-0001-modular-monolith.md).
- Scheduling and AI/workflow tooling: [ADR 0002](../architecture/adr-0002-scheduling-and-ai-tooling.md).
- PostgreSQL, Flyway, and Testcontainers provide the persistence baseline.
- Spring Scheduling is the intended future scheduling mechanism.
- Spring AI is the intended structured-extraction integration behind an application-owned port.
- Embabel is an optional future evaluation, not a committed component.

## Next milestone

Retrieve and persist the actual source content for stored candidates in a controlled, reproducible way. Structured extraction with Spring AI starts only after source content is available.
