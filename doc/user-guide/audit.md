# Audit log

`/audit` shows the audit log for your organization — a record of every
state-changing event Majordomo has emitted. It's primarily for
debugging ("when did the property get archived?", "who created that
contact?") and team-visibility scenarios.

## What's recorded

Each row carries:

- **When** — UTC timestamp of the event.
- **Actor** — the user who triggered it, resolved by username. Blank
  for system-driven events (e.g. scheduled jobs).
- **Action** — `CREATE`, `UPDATE`, `ARCHIVE`, `APPLY`, `DISMISS`, etc.
- **Entity** — type label (`PROPERTY`, `CONTACT`, `JOB_POSTING`,
  `SCORE_REPORT`, `USER`, …).
- **ID** — the entity's UUID.

Events that emit audit entries today:

- Property archived
- User created (organization membership grant)
- Job posting ingested / scored / applied / dismissed
- (More to come — see follow-ups in the issue tracker.)

## Filtering

The filter strip narrows by:

- **Entity type** — datalist of values seen on the current page.
- **Actor** — text-match on username.
- **Since / Until** — date pickers (YYYY-MM-DD).

All filters combine; leaving any blank skips that filter. Results are
newest-first, capped at 50 rows per page (pagination is on the
roadmap).

## Cross-org safety

The page filters strictly on `organization_id`. Entries from other
orgs never appear, even for users who belong to multiple orgs (only
the current org's entries show up).

## What's not yet here

- Pagination beyond 50 rows.
- A diff column — `diff_json` exists on each entry but the UI doesn't
  render it.
- `ServiceRecordCreated` events — the underlying domain event doesn't
  yet carry an `organizationId`, so those entries land with a `null`
  org and are filtered out. Tracked as a follow-up to issue #242.
