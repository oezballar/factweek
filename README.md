# Factweek

Factweek is a narrative-free, personalized weekly briefing of relevant and evidenced facts.

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
| `ingestion` | Shared source capture, document retrieval, and access to fetched documents |
| `processing` | Persisted section classification of fetched source documents; it knows no Technology or Economy fact models |
| `provenance` | Own shared source references and source types |
| `technology` | Own the reviewed technology-fact domain and publication rules |
| `economy` | Own directly published, evidenced economy facts and their structured measurements |
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

Fact Proposals require an explicit human decision. `suggestedEvidenceLevel` is only the LLM suggestion, `reviewedEvidenceLevel` is the explicit reviewer decision, and `TechnologyFact.evidenceLevel` is final. Their only state transitions are `PROPOSED -> ACCEPTED` and `PROPOSED -> REJECTED`; both decision states are terminal.

Accept a proposal with the reviewer-supplied event classification, readiness, and date. An explicitly supplied reviewer date takes precedence over an extracted proposal date; if omitted, `occurredOn` may be taken from the proposal only when the source explicitly supports that event date. If neither provides an adequately supported date, the fact is accepted with an unknown `occurredOn`; publication, creation, review, and current dates are never used as fallbacks. The example date is supplied by the reviewer; it is not inferred automatically:

```bash
curl -X POST 'http://localhost:8080/api/v1/technology/fact-proposals/PROPOSAL_ID/accept' \
  -H 'Content-Type: application/json' \
  -d '{
    "eventType": "TECHNOLOGY_DEPLOYED",
    "readiness": "PRODUCTION_USE",
    "evidenceLevel": "PRIMARY_CONFIRMED",
    "occurredOn": "2026-09-11"
  }'
```

With one source, only an explicitly selected `PRIMARY_CONFIRMED` level is currently accepted, and only for `PRIMARY_DOCUMENT`, `PAPER`, `DATASET`, `REPOSITORY`, or `REGULATOR`. `REPORTED` and `DOCUMENTED` cannot be final Technology Fact evidence levels. A suitable source type does not cause automatic Evidenzhochstufung. `NEWS_REPORT` requires independent confirmation that is not implemented yet; `INDEPENDENTLY_CONFIRMED` and `PROVEN_IN_USE` are not allowed with one source. Missing or unknown evidence levels return 400, decided proposals return 409, and a syntactically valid but unsuitable evidence decision returns 422.

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

Each selected source document causes a billable OpenAI API request. `OPENAI_MODEL`, `OPENAI_PROMPT_VERSION`, `OPENAI_MAX_PROPOSALS_PER_DOCUMENT` (maximum 5), and `OPENAI_MAX_OUTPUT_TOKENS` are optional tuning variables. `OPENAI_MAX_OUTPUT_TOKENS` defaults to `8000`; it is an output ceiling, not a guaranteed token consumption. The adapter keeps OpenAI native Structured Outputs and treats empty or invalid responses as failed extraction attempts without logging response content. Do not put a key in configuration files. Disable the adapter by leaving `FACTWEEK_OPENAI_ENABLED` unset or `false` and `SPRING_AI_MODEL_CHAT=none`.

The OpenAI adapter uses a dedicated Strict Structured Outputs schema rather than Spring AI's generated Kotlin DTO schema. Nullable DTO properties with default values are otherwise emitted as optional properties, while OpenAI requires every object property to appear in `required`. The adapter schema instead makes every property required, represents the optional occurrence date as `string | null`, and disables additional properties recursively. A regression test verifies these invariants for every nested object.

`FACTWEEK_OPENAI_SMOKE_TEST=true ./gradlew test --tests dev.factweek.technology.internal.OpenAiFactProposalSmokeTest` runs an explicitly opt-in, billable smoke test; it is skipped by default. Ollama remains a future interchangeable adapter behind the same application port.

Read the current factual technology briefing:

```bash
curl 'http://localhost:8080/api/v1/briefings/technology/current'
```

`current` is a rolling seven-calendar-day window, not an ISO calendar week: it includes today and the preceding six calendar days. For example, a Sunday request covers Monday through Sunday. The endpoint returns facts and transparent selection metadata only; it does not generate narrative text. `maximum` defaults to `10` and accepts values from `1` through `50`.

`occurredOn` remains exclusively the date on which the asserted event happened. Each source may additionally carry `publishedAt`, the publication timestamp of that concrete source. Discovery and fetch timestamps are technical ingestion metadata and are never substituted for either value. Briefing selection prefers `occurredOn`; only when it is unknown does it use the earliest reliable source `publishedAt` (as a UTC calendar date). Each briefing item exposes `referenceDate` and `referenceDateBasis`; facts with neither value are not assigned to a dated briefing.

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
    "sources": [{"url": "https://example.org/source", "publisher": "Example", "sourceType": "NEWS_REPORT", "publishedAt": "2026-09-11T08:00:00Z"}],
    "referenceDate": "2026-09-11",
    "referenceDateBasis": "OCCURRED_ON"
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
      "sourceType": "PAPER",
      "publishedAt": "2026-09-10T08:00:00Z"
    }]
  }'
```

## Economy facts

Economy facts are factual records, not narratives or forecasts. `occurredOn` is only the date of an actual economic event. A periodic `referencePeriod` identifies the period measured, while `sources[].publishedAt` identifies the publication time of a concrete source; none of these values substitutes for another.

The initial measurement units are `PERCENT`, `PERCENTAGE_POINTS`, and `COUNT`. A periodic indicator requires both a measurement and a complete month, quarter, or year reference period. Other currently supported economy event types do not accept measurements. Values support at most 20 required integer digits and 10 required decimal digits; values beyond those limits are rejected rather than rounded.

`PRIMARY_CONFIRMED` requires at least one primary source (`PRIMARY_DOCUMENT`, `PAPER`, `DATASET`, `REPOSITORY`, or `REGULATOR`); a `NEWS_REPORT` alone is insufficient. `INDEPENDENTLY_CONFIRMED` additionally requires at least two distinct non-empty URLs and publisher names, with at least one primary source. This is a structural minimum, not an automatic editorial judgment of independence.

Publish a discrete event:

```bash
curl -X POST 'http://localhost:8080/api/v1/economy/facts' \
  -H 'Content-Type: application/json' \
  -d '{"statement":"The central bank decided its policy rate.","category":"MONETARY_POLICY","eventType":"MONETARY_POLICY_DECIDED","evidenceLevel":"PRIMARY_CONFIRMED","occurredOn":"2026-09-10","sources":[{"url":"https://example.org/decision","publisher":"Example Central Bank","sourceType":"PRIMARY_DOCUMENT"}]}'
```

## Economy briefing

`GET /api/v1/briefings/economy/current` returns a factual rolling seven-calendar-day selection. It accepts `maximum` (`1..50`, default `10`) and repeated `categories` parameters. Selection prefers `occurredOn`; when it is absent, it uses the earliest known `sources[].publishedAt` as a UTC calendar date. `referencePeriod` never determines briefing inclusion. Facts without either an event date or a source publication time are excluded. Each flat item exposes `referenceDate` and `referenceDateBasis`; the endpoint adds no narrative or forecasts.

```bash
curl 'http://localhost:8080/api/v1/briefings/economy/current?categories=PRICES_AND_INFLATION&maximum=5'
```

The response remains flat and factual. For a periodic indicator, the source
publication date can provide the briefing reference while `referencePeriod`
continues to describe the measured period:

```json
{
  "from": "2026-09-09",
  "to": "2026-09-15",
  "generatedAt": "2026-09-15T09:00:00Z",
  "appliedCategories": ["PRICES_AND_INFLATION"],
  "requestedMaximum": 5,
  "factCount": 1,
  "facts": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "statement": "Inflation was reported for August 2026.",
      "category": "PRICES_AND_INFLATION",
      "eventType": "INDICATOR_VALUE_REPORTED",
      "evidenceLevel": "PRIMARY_CONFIRMED",
      "referencePeriod": {
        "from": "2026-08-01",
        "to": "2026-08-31",
        "granularity": "MONTH"
      },
      "measurement": { "value": 2.4, "unit": "PERCENT" },
      "sources": [
        {
          "url": "https://example.org/inflation",
          "publisher": "Example Statistics Office",
          "sourceType": "PRIMARY_DOCUMENT",
          "publishedAt": "2026-09-10T08:00:00Z"
        }
      ],
      "referenceDate": "2026-09-10",
      "referenceDateBasis": "SOURCE_PUBLISHED_AT"
    }
  ]
}
```

Publish a periodic indicator without turning its reference period or source publication into `occurredOn`:

```bash
curl -X POST 'http://localhost:8080/api/v1/economy/facts' \
  -H 'Content-Type: application/json' \
  -d '{"statement":"Inflation was reported for August 2026.","category":"PRICES_AND_INFLATION","eventType":"INDICATOR_VALUE_REPORTED","evidenceLevel":"PRIMARY_CONFIRMED","referencePeriod":{"from":"2026-08-01","to":"2026-08-31","granularity":"MONTH"},"measurement":{"value":2.4,"unit":"PERCENT"},"sources":[{"url":"https://example.org/inflation","publisher":"Example Statistics Office","sourceType":"PRIMARY_DOCUMENT","publishedAt":"2026-09-10T08:00:00Z"}]}'
```

## Architectural rules

- An article is source material, not a fact.
- LLM output is never published directly.
- Deterministic domain rules remain ordinary Kotlin code.
- AI integrations must return typed data and be evaluated.
- A new infrastructure component needs a demonstrated use case.

## Article section classification

Ingestion remains shared and provider-neutral; there is no separate Economy ingestion. A successfully fetched source document can be classified manually for `technology`, `economy`, both, or neither. Classification is stored separately from extraction and review and does not create facts.

The configured catalogue defines classification targets, not available extractors. Add a target only by configuration; a later processor for that target remains a separate change:

```yaml
factweek:
  article-classification:
    version: article-section-classification-v1
    sections:
      - id: technology
        description: Concrete technological developments, research, and technical applications.
      - id: economy
        description: Concrete economic events and published economic indicators.
    openai:
      enabled: true
      model: gpt-5-mini
```

To activate the OpenAI classifier, set `FACTWEEK_ARTICLE_CLASSIFICATION_OPENAI_ENABLED=true`, provide `OPENAI_API_KEY` through the environment, and use `SPRING_AI_MODEL_CHAT=openai`. `ARTICLE_CLASSIFICATION_OPENAI_MODEL` optionally overrides `OPENAI_MODEL`. Never place an API key in YAML. Increase `ARTICLE_CLASSIFICATION_VERSION` deliberately when the classification prompt or catalogue changes; there is no automatic historical reclassification.

```bash
curl -X POST 'http://localhost:8080/api/v1/processing/documents/<document-id>/classification'
curl 'http://localhost:8080/api/v1/processing/documents/<document-id>/classification'
```

The response contains only the document identity, version, classification time, and section keys:

```json
{
  "documentId": "11111111-1111-1111-1111-111111111111",
  "classificationVersion": "article-section-classification-v1",
  "classifiedAt": "2026-09-15T10:00:00Z",
  "sections": ["economy", "technology"]
}
```

Zero, one, or multiple sections are valid; zero is represented as `[]`. Successful results, including empty ones, are reused for the same document and version without another model call. Two parallel first calls may both call the model, but the database stores one immutable result and both callers receive it. The identity is document ID plus classification version because regular ingestion paths never replace a successfully fetched document's content. Stored results remain readable and reusable when the classifier is disabled. Classification only selects future processing candidates; it neither starts extraction nor publishes a fact.

Invalid document IDs return `400`; an unknown document or absent current-version result returns `404`; a known document that is not fetched returns `409`. Model and invalid structured-output failures return `502`; an enabled classification request without an available classifier returns `503`. All use `application/problem+json`.

## Planned tooling

- Spring Scheduling is the intended later solution for configurable scheduled GDELT imports; the manual import endpoint remains available.
- Spring AI provides structured fact-proposal extraction behind an application-owned port; other providers such as Ollama can later implement the same port.
- Embabel is only an optional future evaluation for demonstrated multi-step or agentic needs.
- n8n and Flowise are not part of the planned architecture.

## Next slice

Connect the existing Technology processing to stored section classifications. Economy proposals and review follow separately.
