# AI Interview Coach — Design Docs

Status: **Approved (2026-11-07)** with the change *backend = Spring Boot*. These docs are the living design and are
updated as phases land.

| # | Document | What's inside |
|---|---|---|
| 1 | [PRD](01-prd.md) | Problem, goals, personas, MVP vs Phase-2 scope, user stories with Given/When/Then acceptance criteria |
| 2 | [Architecture](02-architecture.md) | Stack choices + trade-offs, container diagram, component responsibilities, sequence diagrams (stream, evaluation, resume, code run), cross-cutting concerns, deployment, degradation |
| 3 | [Data model](03-data-model.md) | Design-time Mermaid ER diagrams (4 domains, 32 tables as designed), enums, competency weights per mode, indexes and constraints, modelling decisions |
| 4 | [LLD](04-lld.md) | State machine + transition table, engine (State/Factory/Observer), rounds (Strategy/Template Method), AI layer (Strategy/Decorator/Factory/Facade), evaluation pipeline, code execution, pattern index |
| 5 | [API spec](05-api-spec.md) | Every endpoint: method, path, auth, request, response, errors; SSE event contract; error catalogue; rate-limit classes |
| 6 | [Folder structure](06-folder-structure.md) | Backend (Maven, package-by-feature) and frontend layout, ArchUnit layer rules, naming conventions |
| 7 | [Prompts](07-prompts.md) | Template format, injection framing, interviewer prompt per mode, turn assessment, hints, resume extraction, question generation, evaluation prompt + JSON Schema, repair loop, prompt tests |
| 8 | [Milestones](08-milestones.md) | Phases 1–12 with deliverables, tests, verification commands and Definition of Done |
| 9 | [Risks](09-risks.md) | 16 risks with likelihood/impact and layered mitigations |
| 10 | [Architecture and ER diagrams (as built)](10-architecture-and-er-diagrams.md) | Diagrams drawn from the v1.0.0 code: system architecture, compose containers, backend module dependencies, session lifecycle, streamed answer flow, and the ER diagram of the 21 shipped tables |

## Key decisions at a glance

1. **Backend: Java 25 LTS + Spring Boot 3.5** (Spring MVC on virtual threads), Spring Data JPA + Flyway, Spring
   Security, Maven Wrapper. **Frontend: React + Vite + TypeScript**, with API types generated from the backend's
   OpenAPI document.
2. **SSE over Redis Streams** for streaming. It gives stateless replicas, `Last-Event-ID` replay on reconnect,
   and decouples the POST that triggers generation from the GET that streams it.
3. **Same jar, two roles**: profile `api` (HTTP) and profile `worker` (JobRunr background jobs for resume
   parsing, embeddings, evaluation and the inactivity sweeper).
4. **The LLM never computes totals.** It scores rubric criteria with verbatim evidence. Code verifies the
   evidence, blends objective signals (tests, hints) and computes weighted scores.
5. **Decorator chain around every LLM call**: metering → budget → cache → circuit breaker → retry → timeout,
   using Resilience4j primitives inside explicit decorator classes.
6. **Mock everything external** (`AI_PROVIDER=mock`, `CODE_EXECUTOR=mock`, `PAYMENTS_PROVIDER=mock`). Mock modes are
   deterministic, realistic, labelled in the UI, and refuse the `prod` profile where unsafe.
7. **Tests**: JUnit 5 + Mockito + AssertJ, Testcontainers (real pgvector + Redis), WireMock for third-party HTTP,
   ArchUnit for layering; Vitest + RTL for the web; Playwright for E2E.
8. **AI layer is built before the question bank** (embeddings need it), and **evaluation ships with Phase 5**
   so no phase ends with a stubbed pipeline.

## Defaults taken for the open questions (change any time)

| Question | Default |
|---|---|
| Judge0 fallback if it can't run locally | Decide after the Phase 1 spike. Hosted Judge0 CE (RapidAPI) is the least invasive fallback. |
| AI provider | Mock until you provide a key; the Claude adapter is the primary real provider |
| DSA languages | Python, Java, C++, JavaScript |
| Git | [github.com/squidchemistry/interviewcoach](https://github.com/squidchemistry/interviewcoach) |
