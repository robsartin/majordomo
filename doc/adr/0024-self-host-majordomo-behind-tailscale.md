# 24. Self-host majordomo on a home server, reached over Tailscale

Date: 2026-09-19

## Status

Accepted

## Context

Majordomo has not been hosted anywhere. It has run only on a laptop under
`docker-compose`, and until recently that cost nothing: there were no users and
nothing to reach.

Two things changed. The catalog now holds a full personal profile — household
inventory, contacts, maintenance history, finances, job-search history, and a
book collection. And the Librarian's interest-graph sync (ADR-0023, #319) put a
hard constraint on where the application can run.

**Segue is loopback-only by design.** It binds to `127.0.0.1`, has no
authentication at all, and its host allowlist admits only `localhost`,
`127.0.0.1` and `[::1]`. Segue's own ADR-28 states that making it reachable from
anywhere else is "a deliberate configuration change with its own security
review". So majordomo can only sync to Segue from the same machine. That is not
a deployment detail; it eliminates whole categories of host.

## Decision

We will self-host majordomo on a home server and reach it over Tailscale.

### Rejected: a managed PaaS (Render)

Operationally the easiest option, and the one we already know from
setlist-scout. It loses on two counts, either of which would be enough.

It **breaks the Segue sync outright**. Segue would have to be hosted too, and
hardened first — it has no authentication whatsoever, so exposing it is not a
configuration change but a security project. Shipping #319 and then hosting
somewhere that cannot use it would be building a feature and immediately
switching it off.

It also puts household financial records on a third party for a single-user
application, which buys convenience we can get another way.

### Rejected: the LAN-only Kubernetes cluster

Free, private, and Segue could run in the same namespace, so the sync survives.
It loses on reach: LAN-only means no access away from home, and the honest fix
for that is Tailscale — at which point Tailscale is doing the work and
Kubernetes is only adding a paused, unrelated migration to the critical path.

### Why Tailscale rather than port-forwarding

A tailnet gives a private, authenticated path from a phone without opening a
port on the house router, without a public DNS name, and without terminating TLS
ourselves. The alternative — forwarding a port and running a certificate — puts
an internet-facing surface on a machine holding financial records, to serve one
person.

### Consequence for Segue

Segue must run on the same host as majordomo. `librarian.segue.endpoint` stays
loopback by default and is configurable for the container case, where
`127.0.0.1` means the container rather than the host.

## Consequences

### Good

- **The Segue sync keeps working**, which is the point of having built it.
- **Nothing personal leaves the house.** Finances, contacts and the catalog stay
  on hardware we own.
- **The observability stack survives intact.** `docker-compose.yml` already
  describes Prometheus and Grafana, so ADR-0008 and ADR-0009 hold rather than
  needing an amendment to say we fell back to a provider's metrics.
- **No recurring cost.**

### Bad / accepted trade-offs

- **We own uptime and backups.** Nobody else is watching the disk. Postgres
  backups are now a real obligation rather than someone else's default, and this
  ADR does not discharge it — see the follow-up work.
- **A tailnet is a dependency.** If Tailscale is down or a device is logged out,
  majordomo is unreachable from away, even though it is running fine.
- **Upgrades are manual.** No build pipeline pushes for us; a deploy is a pull
  and a `docker compose up`.
- **The home server becomes infrastructure.** Rebooting it for unrelated reasons
  takes the catalog offline.

## References

- ADR-0023 (Librarian), amended 2026-09-19 for the Segue MCP transport
- Segue ADR-28 (loopback binding, no authentication, host allowlist)
- ADR-0008 (Grafana), ADR-0009 (Prometheus) — preserved by this choice
- Issue #326
