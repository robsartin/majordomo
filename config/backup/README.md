# Backup secrets

Two files belong here and neither is in git. `docker-compose.yml` mounts this
directory into the backup sidecar read-only.

## `identity.age` — the decryption key

```bash
docker compose run --rm --entrypoint age-keygen backup -o /dev/stdout \
  | tee config/backup/identity.age | grep 'public key'
```

Put the public key in `.env` as `BACKUP_AGE_RECIPIENT`.

**Then put a copy of `identity.age` somewhere that is not this house** — a
password manager entry is enough, it is a single line. Every backup is
encrypted to it, so without it the archives are permanently unreadable. Losing
this key and losing the server on the same day is the same as having no
backups at all, which is the situation the backups were meant to end.

## `rclone.conf` — where copies go

One remote per destination named in `BACKUP_REMOTES`. Configure interactively:

```bash
docker compose run --rm --entrypoint rclone backup config --config /config/rclone.conf
```

This file holds the object-store credentials, so it stays out of git along
with the key.
