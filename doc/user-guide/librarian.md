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

## Finding things

`/librarian` lists the catalog and filters by status, by confidence, and by a
substring of the title or author. Filtering on `LOW` confidence is the quickest
way to see what the transcription left doubtful.
