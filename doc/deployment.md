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

## What survives an upgrade

Every piece of durable state is on a named volume. Anything not on this list
lives in a container's writable layer and is gone the next time that container
is rebuilt.

| Data | Volume | Notes |
|---|---|---|
| PostgreSQL | `majordomo-data` | The whole application state |
| Attachments | `majordomo-attachments` | Mounted at `/var/lib/majordomo/attachments` |
| Prometheus | `prometheus-data` | Metrics history; dashboards live in `config/` |
| Grafana | `grafana-data` | As above |
| Backup archives | `majordomo-backups` | Encrypted; also copied off the machine |
| Redis | *(none, deliberately)* | Cache only — 5-minute TTL, evicted on domain events |

Attachments needed that mount and did not have it until #338: the app wrote
them to `./data/attachments`, relative to the image's `WORKDIR`, so every
`--build` destroyed them while the database rows went on pointing at files that
were no longer there. The container is told its directory absolutely, via
`MAJORDOMO_STORAGE_BASE_DIR`, and a test parses this repo's `docker-compose.yml`
to check the mount and the write path still agree.

Note that `docker compose down -v` removes named volumes. `down` on its own does
not.

## Backups

A sidecar takes an encrypted archive of the database and the attachments
nightly, copies it off the machine, and restores it into a scratch database
every seventh run to check the data actually comes back. ADR-0025 records why
it is shaped that way; `doc/backup.md` is the runbook, including the one thing
most likely to go wrong — the decryption key has to live somewhere that is not
this house.

## Upgrading

No pipeline deploys for us:

```bash
git pull && docker compose up -d --build
```

Flyway migrates on startup. Migrations are forward-only, so a rollback means
restoring the archive taken before the upgrade — see `doc/backup.md`. Worth
confirming last night's backup exists before starting, rather than after
discovering the migration was a mistake.
