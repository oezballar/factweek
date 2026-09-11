# Factweek

Factweek is a narrative-free, personalized weekly briefing of relevant and evidenced technology changes.

The repository is deliberately built as a Spring Modulith modular monolith: one deployable application with explicit, automatically verified domain boundaries.

## Technology

- Java 25 toolchain
- Kotlin 2.4.20
- Spring Boot 4.1.1
- Spring Modulith 2.1.1
- Spring AI 2.0.1 (OpenAI adapter, opt-in)
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

PostgreSQL 18 uses `/var/lib/postgresql` as the Compose volume mount path. The Compose setup therefore uses the separate `factweek-postgres-18` volume and leaves older PostgreSQL volumes untouched.

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

Retrieve source content for stored candidates without exposing the stored text publicly:

```bash
curl -X POST 'http://localhost:8080/api/v1/ingestion/source-content/fetches?maximum=10'
```

Stored source content is input material for later evaluation and extraction; it is not a verified fact. Source URLs are checked before every request, including redirects. DNS resolution checks cannot prevent every possible DNS-rebinding scenario; a future public or multi-tenant deployment should evaluate controlled egress.

Candidates can also be captured manually, without a GDELT request. `discoveryProvider` (`GDELT` or `MANUAL`) records how a candidate was found; `sourceType` records the supplied kind of source and is not a trust decision. GDELT candidates are initially classified as `NEWS_REPORT`. Capture only stores metadata and does not fetch content or call AI. The next pipeline step is the existing source-content fetch endpoint.

```bash
curl -X POST 'http://localhost:8080/api/v1/ingestion/candidates' \
  -H 'Content-Type: application/json' \
  -d '{"sourceUrl":"https://example.org/research/result","title":"New battery result","publisher":"NASA","publishedAt":"2026-09-11T08:00:00Z","language":"en","sourceType":"PRIMARY_DOCUMENT"}'
```

Scheme and host case, default HTTP(S) ports, and an empty path are canonicalized; query parameters are retained. A repeated manual request with identical canonical URL and metadata returns the existing candidate. Different title, publisher, publication time, or language for the same canonical URL returns `409`; stored metadata is never overwritten.

Fact-proposal persistence and the Spring-AI OpenAI adapter are available for controlled manual processing. A `FactProposal` is an unconfirmed model suggestion, not a `TechnologyFact`; it has a separate lifecycle and is never published automatically.

Fact Proposals require an explicit human decision. Their only state transitions are `PROPOSED -> ACCEPTED` and `PROPOSED -> REJECTED`; both decision states are terminal. Accepting creates one traceable TechnologyFact in the same transaction, while rejecting requires a documented reason and creates no fact. The proposal retains the original statement and evidence; its link to the resulting TechnologyFact preserves the review provenance.

Accept a proposal with the reviewer-supplied event classification, readiness, and date. The example date is supplied by the reviewer; it is not inferred automatically. `occurredOn` may be omitted only when the proposal already contains an extracted date:

```bash
curl -X POST 'http://localhost:8080/api/v1/technology/fact-proposals/PROPOSAL_ID/accept' \
  -H 'Content-Type: application/json' \
  -d '{
    "eventType": "TECHNOLOGY_DEPLOYED",
    "readiness": "PRODUCTION_USE",
    "occurredOn": "2026-09-11"
  }'
```

Reject a proposal with a non-empty reason:

```bash
curl -X POST 'http://localhost:8080/api/v1/technology/fact-proposals/PROPOSAL_ID/reject' \
  -H 'Content-Type: application/json' \
  -d '{"reason":"Insufficient independent corroboration."}'
```

The OpenAI adapter is disabled by default. To enable it locally, provide a key only through the environment and enable both the application adapter and Spring AI's OpenAI model:

```bash
export OPENAI_API_KEY='replace-with-your-key'
export FACTWEEK_OPENAI_ENABLED=true
export SPRING_AI_MODEL_CHAT=openai
export OPENAI_MODEL=gpt-5-mini # optional
./gradlew bootRun
```

Then trigger at most one selected source document:

```bash
curl -X POST 'http://localhost:8080/api/v1/technology/fact-proposals/extractions?maximum=1'
```

Each selected source document causes a billable OpenAI API request. `OPENAI_MODEL`, `OPENAI_PROMPT_VERSION`, `OPENAI_MAX_PROPOSALS_PER_DOCUMENT` (maximum 5), and `OPENAI_MAX_OUTPUT_TOKENS` are optional tuning variables. Do not put a key in configuration files. Disable the adapter by leaving `FACTWEEK_OPENAI_ENABLED` unset or `false` and `SPRING_AI_MODEL_CHAT=none`.

The OpenAI adapter uses a dedicated Strict Structured Outputs schema rather than Spring AI's generated Kotlin DTO schema. Nullable DTO properties with default values are otherwise emitted as optional properties, while OpenAI requires every object property to appear in `required`. The adapter schema instead makes every property required, represents the optional occurrence date as `string | null`, and disables additional properties recursively. A regression test verifies these invariants for every nested object.

`FACTWEEK_OPENAI_SMOKE_TEST=true ./gradlew test --tests dev.factweek.technology.internal.OpenAiFactProposalSmokeTest` runs an explicitly opt-in, billable smoke test; it is skipped by default. Ollama remains a future interchangeable adapter behind the same application port.

Read the current factual technology briefing:

```bash
curl 'http://localhost:8080/api/v1/briefings/technology/current'
```

`current` is a rolling seven-calendar-day window, not an ISO calendar week: it includes today and the preceding six calendar days. For example, a Sunday request covers Monday through Sunday. The endpoint returns facts and transparent selection metadata only; it does not generate narrative text. `maximum` defaults to `10` and accepts values from `1` through `50`.

Request a smaller result set without category filtering:

```bash
curl 'http://localhost:8080/api/v1/briefings/technology/current?maximum=5'
```

Filter by one or more categories by repeating the `categories` parameter:

```bash
curl 'http://localhost:8080/api/v1/briefings/technology/current?categories=AI_AND_SOFTWARE&categories=ENERGY_AND_CLIMATE&maximum=10'
```

The response is a fact structure with the applied range, filters, and limit:

```json
{
  "from": "2026-09-07",
  "to": "2026-09-13",
  "generatedAt": "2026-09-13T12:00:00Z",
  "appliedCategories": ["AI_AND_SOFTWARE", "ENERGY_AND_CLIMATE"],
  "requestedMaximum": 10,
  "factCount": 1,
  "facts": [{
    "statement": "A concrete technology fact.",
    "category": "AI_AND_SOFTWARE",
    "eventType": "TECHNOLOGY_DEPLOYED",
    "readiness": "PRODUCTION_USE",
    "evidenceLevel": "PRIMARY_CONFIRMED",
    "occurredOn": "2026-09-11",
    "entities": [{"name": "Example technology", "type": "TECHNOLOGY"}],
    "sources": [{"url": "https://example.org/source", "publisher": "Example", "sourceType": "NEWS_REPORT"}]
  }]
}
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

## Architectural rules

- An article is source material, not a fact.
- LLM output is never published directly.
- Deterministic domain rules remain ordinary Kotlin code.
- AI integrations must return typed data and be evaluated.
- A new infrastructure component needs a demonstrated use case.

## Planned tooling

- Spring Scheduling is the intended later solution for configurable scheduled GDELT imports; the manual import endpoint remains available.
- Spring AI provides structured fact-proposal extraction behind an application-owned port; other providers such as Ollama can later implement the same port.
- Embabel is only an optional future evaluation for demonstrated multi-step or agentic needs.
- n8n and Flowise are not part of the planned architecture.

## Next slice

Implement human review for deterministically validated FactProposals. Resulting proposals remain subject to review before any TechnologyFact can be published.
