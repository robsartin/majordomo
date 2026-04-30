# Majordomo user guide

Majordomo is your household's head of staff. It tracks **what you own**,
**who can fix it**, **when it needs attention**, and **what it costs you**.

This guide walks through each service from a user's perspective. For
developer docs, see [../architecture.md](../architecture.md).

## Pages

1. [Getting started](getting-started.md) — sign in, your first organization, the dashboard tour.
2. [Properties (the Steward)](properties.md) — add/edit assets, parent/child hierarchy, link contacts, attachments.
3. [Contacts (the Concierge)](contacts.md) — vendors and service pros, addresses, vCard download, link to properties.
4. [Schedules (the Herald)](schedules.md) — recurring maintenance, recording service events, upcoming/overdue.
5. [Ledger](ledger.md) — total spend, projected annual, per-property rollup.
6. [Envoy](envoy.md) — score job postings against rubrics, ingest from URL or paste, compare reports.
7. [Audit log](audit.md) — who did what, when, and on which entity.
8. [API keys](api-keys.md) — mint, revoke, and use programmatic access.

## Conventions

- **Soft delete**: archived rows stay in the database but are hidden from
  UI lists. Nothing is permanently destroyed by user action.
- **Cross-org isolation**: every page checks that the entity you're
  viewing belongs to your organization. Foreign rows return 403.
- **Time zones**: timestamps display in UTC for now. A user-preference
  for locale-aware formatting is on the roadmap.
- **Mobile**: layouts are Tailwind-grid responsive but optimized for
  desktop first.
