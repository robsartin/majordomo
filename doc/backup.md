# Backups

Self-hosting (ADR-0024) moved the data onto a disk in the house and made
backups ours. ADR-0025 is how that is discharged.

A nightly sidecar takes one encrypted archive of both halves of the durable
state — the database and the attachments — keeps 14 locally, copies each one to
every configured destination, and every seventh run restores the newest into a
scratch database to check the data actually comes back.

## Setting it up

1. Generate the key pair and store the private half off-site — see
   [config/backup/README.md](../config/backup/README.md).
2. Put the public key in `.env` as `BACKUP_AGE_RECIPIENT`.
3. Configure destinations in `config/backup/rclone.conf` and name them in
   `BACKUP_REMOTES`.
4. `docker compose up -d backup`

Confirm it is working rather than assuming it:

```bash
docker compose run --rm backup backup.sh && docker compose run --rm backup verify-restore.sh
```

## Restoring

```bash
docker compose run --rm backup restore.sh /backups/majordomo-<stamp>.tar.age
```

Restores into whatever `PGDATABASE` and `ATTACHMENTS_DIR` name, which by
default is the live database — so stop the app first. To rehearse instead,
point `PGDATABASE` at a scratch database, or just run `verify-restore.sh`,
which does exactly that and cleans up after itself.

Flyway migrations are forward-only (ADR-0011), so this is also the rollback
path for a bad migration: restore the archive taken before the upgrade.

## What the verification actually checks

The backup records exact per-table row counts in its manifest at the moment it
is taken. Verification restores into a scratch database, counts again, and
compares, then checks the attachment file count the same way.

This is the point of the whole arrangement. A dump that silently lost rows is
still a well-formed file: it decrypts, it restores, it exits zero. Size checks,
exit codes and "the job ran" dashboards all pass on it. Only replaying it and
counting tells you.

## The thing most likely to go wrong

**The key is the backup.** Every archive is encrypted to
`BACKUP_AGE_RECIPIENT`, and only `config/backup/identity.age` can read them
back. That file lives on the server, which adds nothing to the server's
exposure — the plaintext data is already there — but it means losing the server
and the key together leaves a pile of permanently unreadable files.

Put a copy of the identity somewhere that is not this house. It is one line of
text; a password manager entry is enough.

## Why encrypted at all

ADR-0024 turned down a managed platform partly to keep household financial
records off third parties. Shipping those same records to an object store in
the clear would have handed that back quietly. Encrypting before anything moves
means every destination holds ciphertext and none of them has to be trusted —
which is also what makes an off-site copy acceptable in the first place.
