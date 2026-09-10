# Factweek

Factweek is a narrative-free, personalized weekly briefing of relevant and evidenced technology changes.

The repository is deliberately built as a Spring Modulith modular monolith: one deployable application with explicit, automatically verified domain boundaries.

## Technology

- Java 25 toolchain
- Kotlin 2.4.20
- Spring Boot 4.1.1
- Spring Modulith 2.1.1
- PostgreSQL 18 with pgvector
- Flyway and Testcontainers
- Gradle Kotlin DSL

## Modules

| Module | Responsibility |
| --- | --- |
| `ingestion` | Discover unverified technology candidates from sources such as GDELT |
| `technology` | Own the reviewed technology-fact domain and publication rules |
| `briefing` | Select the current weekly briefing and apply explicit category filters |

See [the product brief](docs/product/product-brief.md), [ADR 0001](docs/architecture/adr-0001-modular-monolith.md), [ADR 0002](docs/architecture/adr-0002-scheduling-and-ai-tooling.md), and [the living project plan](docs/product/project-plan.md).

## Local development

Prerequisites: JDK 25, Gradle 9.x, Docker with Compose.

```bash
docker compose up -d postgres
gradle bootRun
```

Generate and commit the Gradle wrapper once on a machine with Gradle installed:

```bash
gradle wrapper --gradle-version 9.3.0
./gradlew test
```

The application uses `jdbc:postgresql://localhost:5432/factweek` by default. Override it with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.

## Current vertical slice

Import unverified GDELT candidates into the candidate store (this creates candidates, not reviewed or verified technology facts):

```bash
curl -X POST 'http://localhost:8080/api/v1/ingestion/gdelt/imports?query=technology&maximum=25'
```

The import defaults to the preceding seven days. An explicit UTC time window can be supplied:

```bash
curl -X POST 'http://localhost:8080/api/v1/ingestion/gdelt/imports?query=technology&maximum=25&from=2026-09-01T00:00:00Z&to=2026-09-08T00:00:00Z'
```

Preview unverified GDELT candidates without storing them:

```bash
curl 'http://localhost:8080/api/v1/ingestion/gdelt/candidates?query=technology&maximum=25'
```

Read the current reviewed briefing:

```bash
curl 'http://localhost:8080/api/v1/briefings/technology/current'
```

Manually publish a reviewed fact for the first end-to-end slice:

```bash
curl -X POST 'http://localhost:8080/api/v1/technology/facts' \
  -H 'Content-Type: application/json' \
  -d '{
    "statement": "A research team published a reproducible battery result.",
    "category": "ENERGY_AND_CLIMATE",
    "eventType": "RESEARCH_RESULT_PUBLISHED",
    "readiness": "LAB_RESULT",
    "evidenceLevel": "PRIMARY_CONFIRMED",
    "occurredOn": "2026-09-10",
    "entities": [],
    "sources": [{
      "url": "https://example.org/paper",
      "publisher": "Example Journal",
      "sourceType": "PAPER"
    }]
  }'
```

Filtering is explicit:

```bash
curl 'http://localhost:8080/api/v1/briefings/technology/current?categories=AI_AND_SOFTWARE'
```

## Architectural rules

- An article is source material, not a fact.
- LLM output is never published directly.
- Deterministic domain rules remain ordinary Kotlin code.
- AI integrations must return typed data and be evaluated.
- A new infrastructure component needs a demonstrated use case.

## Planned tooling

- Spring Scheduling is the intended later solution for configurable scheduled GDELT imports; the manual import endpoint remains available.
- Spring AI is planned for structured fact-proposal extraction behind an application-owned port.
- Embabel is only an optional future evaluation for demonstrated multi-step or agentic needs.
- n8n and Flowise are not part of the planned architecture.

## Next slice

Retrieve and persist source content for stored candidates. Structured extraction with Spring AI follows once reproducible source content is available.
