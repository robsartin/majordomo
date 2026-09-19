# Deployment

Majordomo is self-hosted on a home server and reached over Tailscale
(ADR-0024). It is not on a managed platform, and that is a decision rather than
an omission: the Librarian's interest-graph sync only works when majordomo and
Segue run on the same machine.

## What has to be true

**Segue runs on the same host.** It binds to `127.0.0.1`, has no authentication,
and admits only loopback hosts (its ADR-28). Majordomo reaches it at
`LIBRARIAN_SEGUE_ENDPOINT`. From inside the app container that is
`host.docker.internal`, **not** `127.0.0.1` — which would be the container
itself, and would fail silently rather than loudly.

**The tailnet is the only way in.** No port is forwarded on the router and no
public DNS name exists. Access from a phone is via Tailscale.

## Bringing it up

```bash
cp .env.example .env   # then fill it in — see below
docker compose up -d
```

`docker-compose.yml` brings up the app, PostgreSQL 18, Redis 7, Prometheus and
Grafana. Keeping the observability pair is part of why self-hosting won:
ADR-0008 and ADR-0009 survive unchanged instead of being amended to say we fell
back to a provider's metrics.

## Configuration

Everything sensitive comes from `.env`, which is gitignored. `.env.example` is
the template.

| Variable | Effect if unset |
|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | Defaults to `majordomo` / `majordomo` — **change these**; once the stack is on the tailnet this password is what protects the catalog |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | App still starts; OAuth sign-in is broken while form login works |
| `ANTHROPIC_API_KEY` | App still starts; Envoy scoring and shelf-photo extraction fail when used |
| `LIBRARIAN_SEGUE_ENDPOINT` | Defaults to the host gateway; wrong value means the Segue sync fails per book |

The pattern worth noticing: **none of these fail at boot.** A missing value
shows up later as a broken feature, so a smoke test of the actual feature is
how you confirm configuration, not a clean startup log.

## Backups — not yet solved

Self-hosting moved the database from someone else's managed service to a disk in
the house. Nothing currently backs it up. ADR-0024 records this as an accepted
cost of the decision, not as something the decision handled.

Until it is addressed, the catalog, contacts and ledger exist in exactly one
place.

## Upgrading

No pipeline deploys for us:

```bash
git pull && docker compose up -d --build
```

Flyway migrates on startup. Migrations are forward-only, so a rollback means
restoring a backup — which is the other reason the gap above matters.
