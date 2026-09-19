# 25. Encrypted, verified backups to a tailnet host and an object store

Date: 2026-09-19

## Status

Accepted

## Context

ADR-0024 moved majordomo onto a home server and listed "we own uptime and
backups" as an accepted cost. It did not discharge it. Nothing backed anything
up: the catalog, contacts, maintenance history, ledger, Envoy reports and the
book catalog existed in exactly one place, on one disk, in one house.

Two things sharpened this. Flyway migrations are forward-only (ADR-0011), so
without a backup there is also no rollback — `doc/deployment.md` already said a
rollback meant restoring a backup that did not exist. And #338 found that
attachments had no durable home at all, which had to be fixed before there was
anything coherent to back up.

The risk self-hosting actually introduced is not a bad migration. It is that
the data and the building are now the same single point of failure.

## Decision

A backup sidecar in the compose stack takes one `age`-encrypted archive nightly
containing a custom-format `pg_dump` and a tar of the attachment volume, keeps
14 locally, copies each to a host on the tailnet and to an object store, and
every seventh run restores the newest into a scratch database and compares row
counts against a manifest written when the backup was taken.

### Rejected: a volume snapshot instead of `pg_dump`

Simpler to take, and it captures the whole data directory without knowing
anything about the schema. It loses on verification, which is the part that
matters: a snapshot can only be checked by mounting it into a matching
PostgreSQL on a matching filesystem, so in practice it is never checked. A dump
restores into any PostgreSQL, which is what makes the nightly verification
cheap enough to actually run. It is also opaque — there is no way to ask a
snapshot how many rows it contains.

### Rejected: off-host only, no off-site

A copy on another machine in the house covers a dead disk and a bad migration,
which are the likely failures. It does not cover the unlikely one, and the
unlikely one is precisely what changed when the data left a datacentre. Half
the point of the exercise would have been missing.

### Rejected: an unencrypted off-site copy

The straightforward version, and it quietly undoes ADR-0024. That decision
turned down a managed platform partly to keep household financial records off
third parties; shipping the same records to an object store in the clear puts
them back on one, with less scrutiny. Encrypting before anything moves means
every destination holds ciphertext and no destination has to be trusted, which
is what makes off-site acceptable rather than a contradiction.

### Rejected: a managed backup service

Convenient, and the same objection applies for the same reason. It also adds a
vendor to a system whose stated advantage is not having one.

### Rejected: cron on the host

The home server already runs cron, so this is less machinery. But cron's
environment is not the container's environment, and the failure mode is a
backup that silently does not happen — the loudest possible symptom is nothing
at all. A sidecar with the same environment as the database it dumps, sleeping
until its next run, fails visibly instead.

### Rejected: backing up from inside the application

Majordomo already has a scheduler, so this needs no new container. It makes the
thing being backed up responsible for backing itself up, so any failure that
takes out the app takes out its backups at the same moment.

## Consequences

- **A rollback path exists.** Restoring the archive taken before an upgrade is
  now the answer to a bad forward-only migration.
- **The key becomes critical.** Every archive is encrypted to one recipient.
  Losing the server and `identity.age` together leaves permanently unreadable
  files, so a copy of the identity has to live outside the house. This is a new
  single point of failure created by the encryption that makes off-site safe —
  accepted, and documented where the operator will see it.
- **The private key sits on the server.** Restore and verification need it.
  This adds nothing to the host's exposure, since the plaintext data is there
  too, but it means the off-site copy is protected against the object store,
  not against someone who already has the server.
- **Verification is real and therefore can fail.** It restores and counts, so
  it will eventually report a genuine mismatch. That is the feature.
- **The object store costs money.** A few dollars a year at this data size,
  against ADR-0024's "no recurring cost". Small, but it is no longer zero.

## References

- ADR-0024 (self-hosting) — this discharges the backup obligation it accepted
- ADR-0011 (Flyway, forward-only) — why a restore is the only rollback
- `doc/backup.md` — setup, restore and what verification checks
- Issues #337, #338
