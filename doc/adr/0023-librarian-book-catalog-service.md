# 23. Librarian: book catalog service with external enrichment

Date: 2026-09-18

## Status

Accepted

Implementation pending. The decisions below are settled; the work is tracked
as issues #313–#321. Unlike ADR-0022, which was written after Envoy shipped,
this ADR precedes the code — the sections below therefore cite the issues that
will ship each decision rather than the ones that did.

## Context

Majordomo's household-staff metaphor (ADR-0002) covers physical assets,
contacts, schedules, finances, and — since ADR-0022 — the evaluation of
written documents against structured criteria. A personal book library is a
natural next tenant: it is a collection of physical objects with metadata, it
benefits from enrichment against public catalogs, and the books someone owns
say something about what they are interested in.

The immediate prompt was concrete. Two shelf photographs were transcribed into
`doc/librarian/bookshelf-2026-09-18.csv`, 57 rows of `Title, Author, Photo,
Notes, Rating`. Roughly half the rows carry a caveat: 11 authors were filled in from
model knowledge because no author was visible on the spine, 12 rows are marked
partly visible or obscured, and 3 are explicitly uncertain. That distribution
is the shape of the problem — transcription from spines is lossy, so any
automated enrichment on top of it will sometimes match confidently and
wrongly.

We needed a service that:

- Ingests a shelf catalog and deduplicates it, so re-importing is safe.
- Enriches each book against public sources (Open Library, with Google Books
  as fallback) and resolves works and authors to Wikidata QIDs.
- **Refuses to silently accept a low-confidence match**, queuing it for human
  review instead — the same approve-before-commit gate Envoy uses for scoring
  and setlist-scout uses for artist expansion.
- Feeds authors and works into Segue, Rob's personal interest graph, so the
  library contributes to the same graph as music and film.

Following ADR-0002 we name it **The Librarian**, and it lives inside the
existing hexagonal monolith as a vertical slice under
`com.majordomo.{layer}.librarian`, exactly like Steward, Concierge, Herald,
Ledger, and Envoy (ADR-0004).

## Decision

We will introduce Librarian as a hexagonal service constrained by the
following decisions. Each records the alternative we rejected and why it lost.

### Librarian lives in majordomo, not a separate application (#313)

**Rejected: a standalone book-catalog app.**

Librarian's pipeline — ingest, enrich, review, present — is the same shape as
Envoy's. Keeping it here reuses PostgreSQL with JSONB, Flyway, the security
and organization model, the Thymeleaf/Tailwind layout (ADR-0019), the
Resilience4j-guarded outbound adapter pattern, and `AnthropicMessageClient`.
A separate application would duplicate all of that to gain nothing we
currently want.

The condition under which that trade flips is worth recording: a separate app
earns its keep only if the library code needs to be public, or needs to be
hosted apart from majordomo. Neither is true today.

### A single-module vertical slice, not a Maven multi-module split (#314)

**Rejected: extracting `librarian` into its own Maven module.**

majordomo is a single-module modular monolith. Its boundaries are the
`{layer}.{service}` package convention plus ArchUnit fitness functions
(ADR-0017) — `HexagonalArchitectureTest` and the slice cycle rule — which fail
the build on violation. Those enforce the boundary at test time without the
build-graph and IDE overhead of a module split. Librarian follows the
convention exactly and adds no suppressions.

### Its own `Book` aggregate, not a Steward `Property` (#314)

**Rejected: modelling a book as a `Property`.**

`Property` is shaped around serial numbers, warranties, and maintenance
schedules. A book needs authors, ISBN, edition, publisher, external
identifiers, copy count, and a rating. Overloading `Property` would mean a
wide table of mutually-irrelevant nullable columns and conditional logic in
every Steward query.

The `Book` aggregate's fields are defined by the domain class in
`domain/model/librarian`; they are deliberately **not** restated here. A field
table copied into an ADR goes stale silently and then misleads.

### Shelf location is a free-text string, not a reference to a Steward `Property` (#314)

**Rejected: modelling the bookcase as a `Property` and referencing it.**

Decided 2026-09-18. The Property reference is the more "correct" model and it
is also speculative structure: it buys nothing until a bookcase actually needs
warranties or maintenance, and it costs a Librarian→Steward dependency plus a
requirement to create furniture rows before a book can be filed.

A free-text `location` is honest about what we know ("bookcase 1, shelf 2")
and can be migrated to a reference later if the need becomes real. Should that
happen, the dependency direction is Librarian → a Steward inbound port only,
never the reverse.

### Segue integration is an outbound port, and every cataloged book syncs (#319)

**Rejected on mechanism: writing directly to Segue's SQLite file at
`~/.segue/segue.db`.**

Segue is a separate Spring Boot application that owns its storage. Writing its
database file from majordomo would couple the two through a format neither
side owns, bypass Segue's own validation, and risk corruption under concurrent
access. Instead `InterestGraphPort` is an outbound port with a Segue adapter —
the same pattern as the notification and LLM adapters — and Segue gains an
ingest endpoint. That endpoint is a prerequisite tracked on the Segue side.

**Rejected on scope: syncing only books rated 3 or higher.**

Decided 2026-09-18, and reconsidered the same day once a `Rating` column was
added to the import schema. That change matters to the record: the first
version of this decision rested partly on ratings being unavailable, and that
reason no longer holds. The decision stands, on better grounds.

Gating on rating would keep Segue a strict taste graph. We reject it because
Segue already models a 1–5 taste rating of its own. Filtering in majordomo
would mean this service making a judgement Segue is better equipped to make,
using a threshold baked into an outbound adapter rather than one the graph's
owner can revisit. It would also let the catalog and the graph drift apart
silently — a book left unrated would simply never appear in Segue, with
nothing to indicate it had been withheld.

So we sync every cataloged book's author and carry the rating as an attribute,
letting Segue weight what it receives rather than having majordomo withhold
it. Owning a book is itself a signal of interest; a book rated 2 is still
information about its owner.

The Wikidata QID is the join key, which is why QID resolution (#318) gates the
sync.

### Enrichment below a confidence threshold goes to review, never straight to the catalog (#317)

**Rejected: auto-applying the best available match.**

Given that 29 of 57 seed rows carry a transcription caveat, an
always-auto-apply policy would write confident-looking wrong identifiers into
the catalog, and a wrong external identifier is corrosive: once persisted it
propagates into Wikidata lookups and then into Segue, where it is far harder
to spot than at the point of match. The review queue is the point of the
enrichment work, not an embellishment on it.

### CSV import first; photo extraction is a later phase (#316, #321)

**Rejected: building in-app photo upload with LLM extraction up front.**

The CSV path already produced 57 rows via a Cowork transcription session, so
it is the honest MVP. In-app extraction (#321) should reuse the import,
enrichment, and review machinery rather than grow a parallel ingest path, and
it can only do that once those exist. When it lands, extracted rows enter at
`MEDIUM`/`LOW` confidence and feed the review queue — an image extractor
produces the same class of uncertainty as a human reading a spine, at higher
volume.

### The CSV is a re-importable source of truth, not a one-shot seed (#316)

**Rejected: treating import as insert-only, skipping rows that already exist.**

Skip-on-duplicate is the obvious reading of "make import idempotent", and it
is a trap. Until the web UI ships (#320), the CSV is the *only* way to record
a rating, a shelf location, or a status change. An importer that skips
existing rows would silently discard every one of those edits — the failure
mode where "already known" is indistinguishable from "unchanged", and the user
gets no signal that their data went nowhere.

Import therefore **upserts** on the dedupe key (normalised title + author).
A re-import may overwrite the user-authored fields — rating, location, status,
tags, notes — and must never clobber a human-reviewed enrichment result, which
is owned by the review queue rather than the spreadsheet.

This is what makes the `Rating` column viable as a data-entry path rather than
a field that can only ever be set once.

## Consequences

### Good

- **The existing machinery carries most of the weight.** Persistence,
  security, org scoping, soft delete, UUIDv7 ids, cursor pagination,
  Resilience4j-guarded outbound calls, and the Thymeleaf layout all apply
  unchanged. The genuinely new code is the domain model, three outbound
  adapters, and a review queue.
- **Wrong identifiers are caught at the cheapest point.** The review gate sits
  between a lossy transcription and everything downstream that trusts an
  identifier. Catching a bad match there costs one click; catching it after it
  has propagated into Segue costs an investigation.
- **Hexagonal boundaries hold.** `domain.model.librarian`,
  `domain.port.in.librarian`, and `domain.port.out.librarian` have no HTTP,
  SQL, or LLM dependencies, enforced by ArchUnit at test time like every other
  service.
- **Segue stays authoritative over its own data.** An ingest endpoint means
  Segue validates what it accepts and records provenance, rather than
  discovering that another application has been writing its tables.
- **The library is re-importable.** Dedupe on normalized title plus author
  means a second shelf photo, or a corrected CSV, merges rather than
  duplicates.

### Bad / accepted trade-offs

- **A review queue is a queue someone has to work.** With 29 of 57 seed rows
  carrying a caveat, the first enrichment pass will generate real review
  effort. That cost is the deliberate price of not writing wrong identifiers,
  but it is a cost, and if the queue is never worked the catalog stays
  half-enriched.
- **Free-text `location` cannot be queried structurally.** "Which books are in
  the bookcase in the study?" is a string match until and unless the Property
  reference arrives. Accepted: shelf location is currently descriptive, not
  something we filter or report on.
- **Syncing every author will put low-signal entries into Segue.** A book
  bought and never read contributes an author edge, and an unrated one carries
  no weight to discount it by. Rated books ride in with their rating attached,
  so Segue can weigh those; unrated ones arrive indistinguishable from
  favourites. majordomo is deliberately not the component making that
  judgement, and the graph will be noisier than a ratings-gated one.
- **Cross-repo coordination.** #319 cannot finish until Segue ships an ingest
  endpoint, which means a second repository and a second review cycle in the
  middle of the sequence. The alternative — writing Segue's SQLite directly —
  was rejected above, and this is the cost of that choice.
- **External catalogs are not authoritative for a personal shelf.** Open
  Library coverage is uneven, particularly for older technical books and
  non-English editions. Some rows will simply not resolve, and `wikidataQid`
  and `openLibraryKey` are nullable for that reason. A book that never
  enriches is still a catalogued book.

## References

- Design brief: `docs/superpowers/plans/2026-09-18-librarian-handoff.md`
- Seed data: `doc/librarian/bookshelf-2026-09-18.csv`
- Issues: #313 (this ADR), #314 (domain + ports), #315 (persistence),
  #316 (CSV import), #317 (enrichment + review), #318 (Wikidata),
  #319 (Segue sync), #320 (UI), #321 (photo extraction)
- ADR-0002 (service naming), ADR-0004 (hexagonal layering),
  ADR-0017 (ArchUnit), ADR-0019 (Thymeleaf + Tailwind), ADR-0022 (Envoy —
  the pattern this service follows)
