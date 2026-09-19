# 26. Extract attachment text with PDFBox, on a scheduled sweep

Date: 2026-09-19

## Status

Accepted

## Context

ADR-0019's search (#291) indexes a property's own fields and its attachment
*filenames*. The documents themselves — manuals, receipts, service reports —
are opaque. A furnace manual is findable as `furnace-manual.pdf` and not
findable by the error code printed inside it, which is what someone standing in
front of a broken furnace actually knows.

Uploads are restricted to PDF, plain text and images
(`majordomo.storage.allowed-types`), at up to 10MB.

## Decision

Extract text with Apache PDFBox, on a scheduled sweep over attachments whose
extraction status is `PENDING`, into a column with a generated `tsvector` that
joins the existing property search.

### Rejected: Apache Tika

The obvious choice, and what the issue first suggested. Tika reads a hundred
formats — but the allowed upload types are PDF, plain text and images, so all
of that breadth is unreachable. What it does bring is a large transitive
dependency tree and the corresponding maintenance and vulnerability surface,
for one format we actually parse. PDFBox is the parser Tika would have
delegated to for PDFs anyway.

If uploads ever accept office documents, this is worth revisiting; Tika would
then be earning its size.

### Rejected: extracting synchronously during upload

Simplest, and search would be correct the instant an upload returned. It loses
twice. It puts a parser on the upload path, so a slow or corrupt document turns
into a slow or failed upload of a file that was in fact stored perfectly well.
And it does nothing for the attachments already in the database, so the
backfill would still need its own separate path.

### Rejected: an asynchronous event listener

Keeps parsing off the request thread without waiting for a sweep. It needs new
infrastructure — `@EnableAsync` and an executor, neither of which this codebase
has — and it still leaves the backfill to a second mechanism.

The sweep covers both with one piece of code. A backfill written as a one-off
script is run once and then rots; this one runs every few minutes forever, so
it is exercised constantly and cannot quietly stop working.

### Rejected: OCR in this change

Images are an allowed upload type, and a photographed receipt is a real case.
Two options exist: Tesseract, which is another binary in the image and is
mediocre on phone photos, or the Anthropic vision API, which is already in the
stack for the Librarian's shelf photos and is much better at them.

Deferred because it is a separate decision with a cost attached, not a detail
of this one. Worth noting for whoever picks it up: household receipts are more
sensitive than book spines, and ADR-0024 declined a managed platform partly to
keep financial records off third parties — so routing them to an external API
deserves its own argument rather than inheriting one.

This change leaves the right queue behind. A scanned document is a PDF with no
text layer, recorded as `EMPTY`, so the set of attachments OCR would help is
already identified rather than needing a survey.

### Rejected: storing the text without a generated column

Computing the `tsvector` in the application would allow a cheaper cap. It also
allows the vector and the text to disagree, which is the failure that never
announces itself. `properties.search_vector` is generated for the same reason.

## Consequences

- **Search is eventually consistent.** A document is findable within the sweep
  interval (five minutes by default), not the instant it is uploaded.
- **Text is capped at 500,000 characters.** A tsvector over 1MB is rejected
  outright, which would make the row impossible to write rather than merely
  hard to search. The tail of an unusually long document is not searchable. The
  cap is verified against a real database at its worst case — entirely distinct
  lexemes — rather than reasoned about.
- **Images are not searchable.** Until OCR, they are recorded `UNSUPPORTED`.
- **`FAILED` and `EMPTY` are terminal.** Nothing retries them, so a corrupt
  file is logged once rather than in every sweep forever. The cost is that a
  file which becomes readable later needs its status reset by hand.
- **A new dependency.** PDFBox, which parses untrusted files, so it is a
  dependency worth keeping current.

## References

- ADR-0019 (Tailwind/UI) and #291 — the search this extends
- ADR-0024 — the third-party reasoning an OCR decision has to engage with
- Issues #298, #338 (why rows can outlive their files)
