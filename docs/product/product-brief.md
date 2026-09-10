# Factweek product brief

## Product statement

Factweek produces a weekly, personalized overview of relevant and evidenced technology changes. It removes opinions, announcements, duplicates, and journalistic narrative and exposes the evidence and maturity behind every fact.

## First user

A professionally active person in Germany who wants to understand meaningful technological change without consuming daily news.

## Job to be done

When the week ends, help me understand in ten minutes which technologies materially changed, so I remain informed without following a continuous news feed.

## First vertical slice

1. Discover technology articles from the previous seven days through GDELT.
2. Represent them as candidates, never as verified facts.
3. Review and publish selected candidates as typed technology facts.
4. Retrieve at most ten published facts for the current weekly briefing.
5. Filter the briefing by explicit technology categories.

## Non-goals for v0.1

- Breaking-news notifications
- Engagement-based recommendations
- Autonomous publication by an LLM
- Native mobile applications
- General-purpose news coverage
- A graph database
- Microservices

## Success criteria

- A reader understands the week's meaningful technology changes in ten minutes.
- Every published fact has at least one source and an explicit evidence level.
- A source article, claim, candidate event, and published fact remain separate concepts.
- Module boundaries are enforced automatically.
- AI-assisted stages can be evaluated against a curated golden dataset.
