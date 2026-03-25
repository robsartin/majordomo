# API Keys

API keys provide stateless, machine-to-machine authentication for programmatic access to the Majordomo API.

## How It Works

- Keys are scoped to an **organization** and authenticate requests to that organization's data.
- The plaintext key is shown **exactly once** at creation time. It cannot be retrieved again.
- Keys are prefixed with `mjd_` for easy identification (e.g., `mjd_a1b2c3...`).
- At rest, only the SHA-256 hash of the key is stored — the plaintext is never persisted.

## Creating a Key

Requires an active session (form login or OAuth2).

```http
POST /api/organizations/{orgId}/api-keys
Content-Type: application/json

{
  "name": "CI Pipeline",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

Response (`201 Created`):

```json
{
  "id": "019...",
  "organizationId": "019...",
  "name": "CI Pipeline",
  "key": "mjd_a1b2c3d4e5f6...",
  "createdAt": "2026-03-25T12:00:00Z",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

Save the `key` value immediately — it will not be shown again.

## Using a Key

Include the key in the `X-API-Key` header on every request:

```http
GET /api/properties?orgId=019...
X-API-Key: mjd_a1b2c3d4e5f6...
```

The `ApiKeyAuthenticationFilter` hashes the provided key with SHA-256 and looks it up in the database. If the key is valid, not expired, and not revoked, the request proceeds as authenticated.

## Listing Keys

```http
GET /api/organizations/{orgId}/api-keys
```

Returns metadata only (ID, name, creation date, expiration). The plaintext key and hash are never returned.

## Revoking a Key

```http
DELETE /api/organizations/{orgId}/api-keys/{keyId}
```

Returns `204 No Content`. Revocation is a soft delete — the key's `archived_at` timestamp is set, and it can no longer authenticate requests.

## Security Notes

- **SHA-256 (not Argon2id)** is used for key hashing because API keys are high-entropy random values (32 bytes), making brute-force infeasible. Argon2id's deliberate slowness would add unnecessary latency to every API call.
- **Expiration** is optional. Keys without an `expiresAt` are valid until revoked.
- **Rotation**: Create a new key, update your client, then revoke the old key.
- Keys are always validated against the database (no JWT-style offline validation), so revocation takes effect immediately.
