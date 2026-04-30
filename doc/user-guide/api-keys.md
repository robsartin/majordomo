# API keys

Majordomo's REST API at `/api/...` accepts two authentication methods:
your session cookie (after logging in via the web UI) **or** a
machine-to-machine API key passed in an `X-API-Key` header.

`/account/api-keys` is where you mint and manage keys.

## When to use a key

- A script that needs to read or update properties without a browser.
- A monitoring agent posting service records.
- A spreadsheet add-on or third-party automation tool.

If a human is going to log in, use the web UI — keys are for
non-interactive callers.

## Minting a key

1. Click your name in the header → **API keys** (or visit
   `/account/api-keys` directly).
2. Type a memorable **name** for the key (e.g. "CI runner", "iOS
   shortcut").
3. Click **+ Create key**.
4. The plaintext key (64 hex characters) appears in a yellow banner
   above the form: **copy it now.** Majordomo only stores the
   SHA-256 hash; once you leave the page the plaintext is gone forever.

## Using a key

```bash
curl -H "X-API-Key: <your-key>" https://your-host/api/properties
```

The key is scoped to the organization that minted it.

## Revoking

Each row in the table has a small **Revoke** button. Click it; the
key is soft-deleted (its `archivedAt` is set) and immediately stops
authenticating. The row disappears from the list (archived keys are
hidden by default).

## Best practices

- **One key per integration** — easier to revoke without breaking
  others.
- **Rotate periodically** — mint a new one, switch over, then revoke
  the old one.
- **Never commit a key** — store in your CI's secret store (GitHub
  Actions secrets, Vault, etc.).
- **Ignore the SHA-256 vs. Argon2id question** — keys are high-entropy
  (32 random bytes) so SHA-256 is appropriate for fast lookup. Don't
  ask Majordomo to use Argon2id on machine credentials; it adds
  latency without security.

## Same data via REST

`POST /api/organizations/{orgId}/api-keys` mints a key (returns the
plaintext in the response). `DELETE /api/organizations/{orgId}/api-keys/{id}`
revokes it.
