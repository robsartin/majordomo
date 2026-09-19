# The Librarian

The Librarian catalogs your physical books, enriches them against public
catalogs, and keeps the doubtful matches out of the catalog until you have
looked at them.

## Getting books in

The shelf is imported from a CSV with the columns
`Title, Author, Photo, Notes, Rating`. `doc/librarian/bookshelf-2026-09-18.csv`
is the current shelf, 57 rows transcribed from two photographs.

**Re-importing is safe, and it is how you edit.** Import matches on a normalised
title-and-author key, so a second import updates the books it already knows
rather than duplicating them. The fields you own in the spreadsheet — rating,
notes, status — are overwritten from the file; identifiers resolved by
enrichment are not, so a re-import never undoes a match you accepted.

A row that cannot be read fails the whole import, naming the line. That is
deliberate: silently skipping a malformed row would leave you unable to tell a
shelf of 57 from a shelf of 57 minus the ones that quietly failed.

## Importing from a photograph

`/librarian` also takes a shelf photograph directly. A vision model transcribes
what it can see and the rows go through **the same importer as the CSV** — same
dedupe, same upsert — so photographing a shelf you have already catalogued
updates it rather than duplicating it.

Two things are deliberately true of photo imports:

- **They never arrive trusted.** However clean a spine looks, a row nobody has
  checked is capped below `HIGH` confidence and lands in the review queue. The
  distinction is not that the model reads worse than a person; it is that the
  CSV passed through you and the photograph did not.
- **The model never sets a rating.** A rating is your judgement, and one it
  volunteers is discarded before it reaches the catalog.

If the photograph cannot be read, you are told so. A failed read is never
reported as "no books found" — you could not tell that apart from a picture of
an empty shelf.

## Confidence, and why it exists

Transcribing a spine is lossy. Each row is graded from its notes:

| Grade | What it means | Example note |
|---|---|---|
| `HIGH` | Read cleanly, or confirmed by a strong match | *(no note)*, `Vertical stack, left side` |
| `MEDIUM` | Read, but imperfectly | `Bottom shelf, partly visible` |
| `LOW` | Not read — supplied or guessed | `filled from knowledge`, `title inferred` |

A note that says nothing about legibility — where the book sits, how many copies
you own — does **not** lower the grade.

## The review queue

`/librarian/review` holds external matches that scored too low to apply on their
own. **Accept** writes the match's identifiers onto the book. **Reject** leaves
the book untouched and records the decision, so the same wrong book is not
offered to you twice.

Matches at or above 0.90 are applied without asking. Everything below waits for
you. The gate exists because a catalog will return something plausible for every
half-read spine, and a wrong identifier spreads: into the Wikidata lookup, and
from there into the interest graph, where it is much harder to notice.

## Sending authors to Segue

Books feed your interest graph. For each book, the Librarian resolves its
authors to Wikidata identifiers and pushes them into Segue, carrying your rating
alongside.

Three rules keep it honest:

- **An author who cannot be resolved unambiguously is skipped**, never pushed
  under a guess. A wrong identifier attributes someone else's work in a graph
  that outlives this catalog.
- **Every catalogued book syncs** — there is no rating threshold. Segue models
  its own taste scale and is better placed to weight what it receives than the
  Librarian is to withhold it. Segue's own guidance is that low ratings are as
  useful as high ones.
- **An unrated book contributes its author but no opinion.** Segue requires a
  rating to record a preference, and the Librarian has none to give, so it adds
  the author and stops there rather than inventing one.

Segue listens only on your own machine and has no password, so this works when
both are running locally. If Segue is not reachable, the sync fails and says so
— the catalog is unaffected.

## Finding things

`/librarian` lists the catalog and filters by status, by confidence, and by a
substring of the title or author. Filtering on `LOW` confidence is the quickest
way to see what the transcription left doubtful.
