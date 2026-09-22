# 28. Envoy: generate application materials grounded in an uploaded résumé

Date: 2026-09-21

## Status

Accepted

## Context

The README frames Envoy as "job-posting scoring & application support". Only
scoring exists (ADR-0022). Issue #290 proposed the missing half: a tailored
cover-letter draft "grounded in the posting + rubric rationale".

That framing does not survive contact with the data. The rubric encodes what
Rob **wants** — compensation bands, remote flexibility, Java/Spring, company
stage — not what he has **done**. A `ScoreReport` explains why a posting suits
him, in terms of the posting. Nowhere in majordomo is there a résumé, an
employment history, or a skill he actually has.

A generator given only those two inputs can do one of two things: write
something true and generic, or write something specific and invented. The
second is not a quality problem. A cover letter is sent, under a real name, to
a company that can check — and a fabricated employer cannot be walked back by
editing the draft afterwards.

## Decision

Generate four material kinds — cover letter, short intro message,
screening-question answers and tailored résumé bullets — from the posting, the
score report's rationale, and **the text of a résumé the user has uploaded**,
with every factual claim carrying the source span it came from and every span
verified against that résumé.

### The grounding source: a résumé attached to the user

The résumé is an ordinary attachment on the `USER` record, which is already a
valid `EntityType`. The extraction sweep from ADR-0026 and ADR-0027 turns the
uploaded PDF into `extracted_text` with no new pipeline, no new upload path and
no new storage decision.

**Rejected: a structured profile entity.** Roles, dates, achievements and
skills as first-class domain objects would give cleaner input to the model and
finer control over emphasis. It creates a second place the CV lives. The two
diverge the first time Rob updates the PDF he actually sends people and not the
record in here, and the divergence is silent — the drafts simply start
describing a slightly wrong person.

**Rejected: pasting the background per request.** No schema, no sync problem,
and it must be done again on every single generation during a job hunt.

**Rejected: the posting and rubric alone.** What #290 literally asked for.
Covered above: it produces filler or fabrication, and the only way to tell
which is to read every draft carefully — which is the work the feature was
supposed to remove.

### The grounding rule, and why a prompt is not enough

**A draft may not assert experience that is not in the source material.**

An instruction in a prompt is a request, not a control. So the model returns
the draft *and* a list of the claims it made, each with the span of source text
it came from, and every span is checked against the résumé deterministically. A
claim whose cited span is not in the source fails the draft.

This is Segue's rule — every relationship carries the provenance of who claimed
it — applied to prose.

**What this catches:** an employer, a job title, a credential or a project that
does not appear in the résumé.

**What it does not catch:** a true span, mischaracterised. If the résumé says
"led a team of 4" and the draft says "led a team of 40" while citing that span,
the span exists and the check passes. Scale, seniority and causation can all be
inflated around a real citation.

The guard is therefore a floor, not a warrant. It makes fabrication-from-
nothing impossible and leaves exaggeration-from-something possible, and the
citations are shown in the UI (#352) so a human reviews what each claim rests
on. Saying otherwise would be the more comfortable claim and the wrong one.

**Rejected: a second LLM call to fact-check the first.** Judgement checking
judgement, at double the cost, with no deterministic component anywhere in it.
A failure mode the checker shares with the writer is invisible to this design.

**Rejected: returning a draft with warnings.** A warning attached to a document
someone is about to send is not a control; it is a note that gets scrolled
past. An uncitable claim fails the generation.

### Storage: versioned records, like score reports

Drafts persist as immutable records alongside `ScoreReport`, which already
solved org scoping, usage capture and historical reproducibility for this exact
shape. Regeneration adds a record rather than replacing one: a draft that was
actually sent to a company is a historical fact, and losing it to a later
regeneration would make "what did I tell them?" unanswerable.

### Tone: per request

Chosen at generation time, not stored as a preference. The register for a
LinkedIn DM to a founder is not the register for a formal cover letter at a
bank, and both happen in the same week.

### A separate port and circuit breaker

Generation gets its own outbound port and its own Resilience4j instance, not
`envoy-llm`. A run of generation failures must not trip the breaker that
scoring depends on, and the reverse — the same reasoning `AnthropicVisionClient`
already applies against `AnthropicMessageClient`.

It also gets its own model property. Worth noting rather than changing here:
`envoy.llm.model` is pinned to `claude-sonnet-4-6`, which is no longer current.
Revisiting that belongs with scoring, not with this feature.

## Consequences

- **Nothing generates until a résumé is uploaded and extracted.** Extraction
  runs on a five-minute sweep, so a résumé uploaded and used immediately is
  still `PENDING`. Generation must refuse and say which of those it is —
  "no résumé", "not read yet" and "could not be read" have three different
  fixes, and collapsing them sends someone looking in the wrong place.
- **The résumé's quality bounds every draft.** A thin PDF produces thin
  letters, and the feature cannot compensate for that without inventing.
- **Drafts accumulate.** They are never overwritten, by design.
- **Résumé bullets are the riskiest kind** and ship last, because they rewrite
  claims about history rather than framing them.
- **Per-generation cost**, on a feature used in bursts during a job hunt.

## Amendment — 2026-09-22

Implementing the guard (#350) closed part of the hole this ADR described.

The decision above says the check "does not catch a true span,
mischaracterised", and gives the example of a résumé saying "led a team of 4"
against a draft saying 40 while citing that span. That example is now caught,
by a second check: **every run of digits in the draft must occur in the résumé
or the posting.** "40" does not appear in a résumé that says 4, so the draft
fails — and the same check catches an invented salary, percentage or duration.

This does not make the guard total, and the original statement of its limits
stands for everything not expressible as a number. "Rebuilt the payments
platform" against a résumé saying "contributed to the payments platform" cites
a real span, states no number, and passes.

It also introduces a false positive the decision above did not anticipate: a
number correctly *derived* rather than quoted. A résumé carrying only dates
supports "over a decade" but not "11 years", because 11 appears nowhere. The
prompt therefore tells the model the rule it is judged against and to prefer
the unquantified phrasing. That is the right way round — the prompt guides so
that honest drafts pass, and the check decides. A guard that fires on honest
work is one that gets switched off.

## References

- ADR-0022 — Envoy scoring, which this completes
- ADR-0026, ADR-0027 — the extraction that makes a PDF résumé usable
- Segue ADR on claim provenance — the pattern the citation guard borrows
- Issues #290 (epic), #347–#352
