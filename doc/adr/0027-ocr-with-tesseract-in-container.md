# 27. OCR scanned documents with Tesseract, in the container

Date: 2026-09-19

## Status

Accepted

## Context

ADR-0026 extracts text from PDFs that have a text layer and from plain text. It
records images as `UNSUPPORTED` and text-layer-less PDFs as `EMPTY`, and says
those are exactly what OCR is for.

The cases that matter are household ones: a photographed receipt, a scanned
manual, and an appliance's rating plate — which is the highest-value of the
three, because `Property.serialNumber` and `modelNumber` are fields we already
have and nobody types a serial number off a sticker willingly.

## Decision

Tesseract, installed in the application image, invoked as a subprocess, behind
the existing `TextExtractionPort` as a fallback when the document's own text
comes back empty or unreadable.

### Rejected, for now: the Anthropic vision API

Better — clearly, not arguably. Phone photographs of creased receipts and
glare-covered rating plates are Tesseract's weakest case and a vision model's
routine one. The client already exists for the Librarian's shelf photos, so the
work would be small.

It loses on a question that is not about accuracy: whether these documents
should leave the house at all. ADR-0024 declined a managed platform partly to
keep household financial records off third parties, and ADR-0025 refused to
send backups off-site in the clear for the same reason. Envoy sends job
postings to Anthropic and the Librarian sends photographs of book spines — but
a receipt carries an address, a card's last four digits and a purchase history,
and inheriting that precedent without arguing it would be the move both of
those ADRs declined to make.

The deciding point is that this choice is **reversible and the other is not**.
Whatever Tesseract cannot read stays `EMPTY`, which is the same queue mechanism
again — so vision can be added later for exactly the leftovers, with evidence
about how many there are and what they look like. Choosing vision first would
send every document to a third party to find out whether that was necessary.

### Rejected: Tesseract for scans, vision for photographs

Principled — flatbed scans are Tesseract's strong case, phone photos its weak
one — and it may well be where this ends up. It builds and maintains two
engines before there is evidence that one is insufficient, and it still sends
the receipts.

### Rejected: vision, opt-in per attachment

Most control, and the default path then does no OCR at all, which is the
outcome the issue was filed to avoid.

### Rejected: tess4j rather than a subprocess

The JNA binding avoids process overhead, at the cost of native library loading,
JVM-level crashes from a native segfault taking the application with them, and
another dependency. A subprocess is isolable, killable on a timeout, and costs
a few hundred milliseconds against an operation that takes seconds anyway.

## Consequences

- **The image grows by about 127MB**, measured.
- **Quality is unproven and that is the point.** After the re-extraction pass,
  the attachments Tesseract could not read are the rows still sitting at
  `EMPTY` or `FAILED`. Counting them is the evidence for or against revisiting
  vision, and it costs nothing to collect.
- **OCR runs on the home server's CPU.** Rendering a PDF page at 200dpi and
  recognising it is seconds of work, so the page cap (20) and the per-image
  timeout (60s) exist to bound what one document can hold up.
- **Long scanned documents are partially indexed** — the first 20 pages.
- **Re-extraction needs a migration.** `EMPTY` and `UNSUPPORTED` are terminal,
  so V24 requeues them. Any future change to extraction needs the same, which
  is a small cost for statuses that never retry on their own.
- **A subprocess timeout had to be built correctly rather than declared.**
  Reading a process's output to the end blocks until it exits, so a timeout
  written around it never fires; the output is drained on its own thread.

## References

- ADR-0026 — the extraction this extends, and the queue it left behind
- ADR-0024, ADR-0025 — the third-party reasoning this engages with
- Issue #341
