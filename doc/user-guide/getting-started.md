# Getting started

## Signing in

Majordomo offers two sign-in paths:

1. **Username + password** at `/login`. Local accounts are seeded for
   development (`robsartin` / `xyzzyPLAN9`). New accounts are created by
   an administrator until self-signup ships.
2. **Google OAuth2**: click "Sign in with Google" on the login page. The
   first sign-in links your Google identity to a Majordomo user; from
   then on, the same Google account lands you back in your existing
   workspace.

Once signed in, you land on `/dashboard`.

## Your organization

Everything in Majordomo — properties, contacts, schedules, audit
entries — is scoped to an **organization**. You're a *member* of one
or more organizations, and each session resolves to your *first*
organization automatically. Switching organizations is on the roadmap;
today, talk to an admin if you need to be added to another one.

## The dashboard

The dashboard at `/dashboard` shows:

- **Top stat cards** — properties count, contacts count, total spend,
  projected annual maintenance. The two spend cards link straight to
  the [Ledger](ledger.md).
- **Upcoming maintenance** — schedules with a `nextDue` date in the
  near future, fed by [Herald](schedules.md).
- **Overdue items** — schedules whose `nextDue` has passed.
- **Recent service records** — most recent maintenance you (or your
  team) logged.
- **Apply-now postings** — Envoy's top 5 recently scored APPLY_NOW
  job postings (only relevant if you're using [Envoy](envoy.md)).

## The sidebar

The left sidebar maps to the major services:

| Link | Goes to |
|---|---|
| Dashboard | `/dashboard` (this page) |
| Properties | [/properties](properties.md) |
| Contacts | [/contacts](contacts.md) |
| Schedules | [/schedules](schedules.md) |
| Ledger | [/ledger](ledger.md) |
| Envoy | [/envoy](envoy.md) |
| Audit | [/audit](audit.md) |

The header has a Sign out button and an "API keys" shortcut to
[your account's API keys](api-keys.md).

## Where to next

- **You own things and want to track them** → start with [Properties](properties.md).
- **You want to remember when stuff needs servicing** → set up [Schedules](schedules.md).
- **You're tracking cost over time** → check the [Ledger](ledger.md).
- **You're using Envoy for job-posting scoring** → start at [Envoy](envoy.md).
