# The Librarian — Design Handoff (pre-plan)

> **Status:** design brief, not yet an implementation plan. Written 2026-09-18 in a Cowork session; handed off to the "Majordomo ready issues" Claude Code session. Next step is to turn this into GitHub issues and an ADR-0023, then a full checkbox plan in the style of `2026-04-23-envoy-job-search.md`.

**Goal:** Add `librarian` — a seventh majordomo service that catalogs Rob's physical book library, enriches each book with external identifiers (Open Library, Wikidata), and syncs authors/works into Segue (Rob's personal interest graph MCP server at `~/code/segue`).

**Seed data:** `doc/librarian/bookshelf-2026-09-18.csv` — 58 rows transcribed from two shelf photos. Columns: `Title, Author, Photo, Notes`. Rows whose Notes say "filled from knowledge" had no author visible on the spine; rows marked "uncertain" or "partly visible" need review before enrichment.

---

## Corrections and answers (added 2026-09-18 on receipt, by the majordomo session)

Verified against the repo; the brief above is left as written.

- **ADRs live in `doc/adr/` (singular), not `docs/adr/`.** ADR-0023 is the
  correct next number — the last is `0022-envoy-job-posting-scoring-service.md`.
- **`V22` is correct** — the last migration on `main` is `V21__calendar_tokens.sql`.
- **The seed CSV has 57 data rows, not 58.** Composition: 28 with no notes,
  11 with the author filled from knowledge, 12 marked partly visible or
  obscured, 3 marked uncertain. No duplicate titles.
- **A `Rating` column was added to the CSV on 2026-09-18**, after the brief was
  written, so the schema is now `Title, Author, Photo, Notes, Rating`. Values
  are being filled in by hand. This reopened the Segue scope question below —
  the answer did not change, but its reasoning did. See ADR-0023.

The three open decisions were answered and are recorded in ADR-0023:

1. **Segue scope** — sync *every* cataloged book's author, carrying the rating
   as an attribute. Originally decided because no seed row had a rating;
   re-decided once the `Rating` column existed, on the stronger ground that
   Segue models its own 1–5 taste rating and is better placed to weight or
   filter than majordomo is to withhold.
2. **Location** — free-text `location` string on `Book`. A Steward `Property`
   reference is speculative structure; revisit when a bookcase needs
   warranties or maintenance.
3. **Photo → rows** — CSV import first, as recommended. In-app image
   extraction is deferred to the last issue.

Issues filed from the breakdown: #313–#321.

---

## Decisions already made

1. **Lives in majordomo, not a new app.** Reasons: same ingest → enrich → review → present shape as Envoy; reuses Postgres/JSONB, Flyway, security, Thymeleaf layout, `AnthropicMessageClient`, and the review-before-commit pattern. A separate app only makes sense if the library code needs to be public or hosted apart from majordomo.
2. **No Maven multi-module split.** majordomo is a single-module modular monolith; boundaries are the `{layer}.{service}` package convention plus ArchUnit (`HexagonalArchitectureTest`, slice cycle rule). Librarian follows that convention exactly: `domain/model/librarian`, `domain/port/in|out/librarian`, `application/librarian`, `adapter/in/web/librarian`, `adapter/out/persistence/librarian`.
3. **Own `Book` aggregate, not a Steward `Property`.** `Property` is shaped around serial numbers/warranties/maintenance; a book needs authors, ISBN, edition, publisher, external IDs, copies, rating. Optionally a `Book` may reference a Steward `Property` (the bookcase) as its home location — dependency direction Librarian → Steward port only, never the reverse.
4. **Segue integration is an outbound port** (`InterestGraphPort`) with a Segue adapter, same pattern as the notification and LLM adapters. Wikidata QID on the book/author is the join key.
5. **Review before commit** for any enrichment match below a confidence threshold (mirrors Envoy and setlist-scout's approve-the-expansion step).

## Open decisions (ask Rob)

- Should Segue receive every book's author, or only books rated 3+?
- Where does shelf/location live: free-text `location` on `Book`, or a reference to a Steward `Property` bookcase?
- Photo → rows: stay in Cowork chat (Claude transcribes, emits CSV) or add an in-app upload that calls `LlmExtractionPort` with the image? Recommendation: CSV import first; image extraction is a later phase.

## Proposed shape

**Domain (`domain/model/librarian`)**
- `Book` — mutable POJO (Property pattern): id (UUIDv7), organizationId, title, subtitle, edition, authors (List<String>), isbn13, publisher, year, location, copies, status (`OWNED|READ|WANT|LOANED`), rating (1–5, nullable), tags, wikidataQid, openLibraryKey, sourcePhoto, confidence (`HIGH|MEDIUM|LOW`), notes, createdAt/updatedAt/archivedAt.
- `BookImportRow` — record, raw CSV row before enrichment.
- `EnrichmentCandidate` — record, one external match with score + payload (JSONB).
- Events: `BookCataloged`, `BookEnriched`, `BookSyncedToInterestGraph`.

**Ports**
- in: `CatalogBooksUseCase` (CSV import with dedupe on normalized title+author), `EnrichBookUseCase`, `ReviewEnrichmentUseCase`, `SyncToInterestGraphUseCase`, `ListBooksUseCase`.
- out: `BookRepository`, `EnrichmentCandidateRepository`, `BookMetadataSource` (Open Library, Google Books fallback), `WikidataLookupPort`, `InterestGraphPort` (Segue).

**Adapters**
- `adapter/out/ingest/librarian/CsvImportSource` — reads the seed CSV schema.
- `adapter/out/metadata/OpenLibraryClient`, `GoogleBooksClient` — `RestClient`, Resilience4j.
- `adapter/out/wikidata/WikidataSparqlClient`.
- `adapter/out/interestgraph/SegueAdapter` — talk to Segue (decide: HTTP endpoint added to Segue vs. direct SQLite write to `~/.segue/segue.db`; prefer an endpoint).
- `adapter/in/web/librarian` — `/librarian` (list, search, review queue, book detail) and `/api/librarian` (import, enrich, sync).

**Persistence**
- `V22__librarian_schema.sql`: `books`, `book_enrichment_candidates` (JSONB payload), `book_interest_graph_links`. Soft delete via `archived_at`, UUIDv7 ids, org-scoped like every other table.

**Docs**
- ADR-0023 "Librarian: book catalog service with external enrichment"
- Row in CLAUDE.md service table; user-guide page.

## Suggested issue breakdown

1. ADR-0023 + CLAUDE.md/README service table entry.
2. Domain model + ports (TDD, no adapters).
3. `V22` migration + JPA persistence adapter + IT.
4. CSV import use case + dedupe (seed with `doc/librarian/bookshelf-2026-09-18.csv`).
5. Open Library metadata adapter + enrichment use case + review queue.
6. Wikidata lookup adapter.
7. `/librarian` Thymeleaf pages (list, detail, review queue) with a11y guardrails.
8. Segue `InterestGraphPort` + adapter (coordinate an ingest endpoint in `~/code/segue`).
9. Later: in-app photo upload → `LlmExtractionPort`.

## Related repos
- `~/code/segue` — interest graph MCP server (Spring Boot, Wikidata-backed, SQLite at `~/.segue/segue.db`, 1–5 taste ratings).
- `~/code/setlist-scout` — concert finder; shares the seed → expand → review → web page pattern and the Google OAuth allow-list. Only structural reuse; no data integration planned.
