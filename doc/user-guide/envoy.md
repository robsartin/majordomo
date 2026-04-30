# Envoy

The **Envoy** is Majordomo's job-posting scoring service (ADR-0022). It
ingests a posting, runs it past Anthropic's Claude with a versioned
**rubric**, and produces a structured **score report** with category
scores, flags, and a recommendation (`APPLY_NOW` / `CONSIDER` / `SKIP`).

## The list page

`/envoy` shows recent score reports for your org. Each row carries:

- Posting title + company
- Total score and recommendation badge
- Confidence
- Rubric name + version
- LLM token usage (input / output / cost where available)

A filter strip narrows by minimum score and recommendation.

## Ingesting a posting

The same `/envoy` page hosts an **Ingest + score** inline form. Three
input modes:

| Mode | What you provide |
|---|---|
| **Manual paste** | Raw posting text. Title / company / location parsed best-effort. |
| **URL** | A public job-posting URL. Envoy fetches and extracts. |
| **Greenhouse** | A Greenhouse board URL or job ID. Uses Greenhouse's structured API. |

After ingest, Envoy automatically scores the posting against the
`default` rubric and redirects to the new score report.

## A score report

`/envoy/reports/{id}` shows:

- **Total score** + recommendation banner
- **Categories**: per-category score, max points, tier label, rationale
  paragraph from the LLM
- **Flags**: any concerns the rubric or LLM raised (compensation
  ambiguity, location mismatch, etc.)
- **Confidence**: 0.0–1.0 — how sure the LLM is about the assessment
- **LLM usage**: input/output tokens and (where the model returns it)
  estimated cost

## Comparing reports

`/envoy/compare?ids=A,B,C&rubric=default` renders a side-by-side
comparison of 2–5 reports under the same rubric. Useful for picking
between two postings or sanity-checking a score.

## Editing a rubric

`/envoy/rubrics` lists every rubric and its current version. Click a
rubric name to edit it; on save, Envoy creates a **new version** rather
than mutating the existing one — old reports continue to reference
their original rubric version. To rescore an old posting against the
new rubric version, use `POST /api/envoy/rescore` (web rescore button
on the report page is on the roadmap).

A rubric is a list of **categories**, each with a `key`, description,
maxPoints, and one or more **tiers**. Tiers define the score bands the
LLM is asked to choose from (e.g. "Excellent: 8–10 points", "Adequate:
4–7", "Poor: 0–3"). The rubric also has **thresholds** — the score
ranges that map to APPLY_NOW / CONSIDER / SKIP recommendations.

See ADR-0022 for the full design rationale.
