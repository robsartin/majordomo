# Cursor-Based Pagination

Majordomo uses cursor-based pagination on all list endpoints. Cursors are UUIDv7 entity IDs, which are time-sortable by design (ADR-0018).

## Why Cursor-Based?

- **Stable**: No skipped or duplicated items when data changes between pages (unlike offset-based)
- **Efficient**: Uses indexed `WHERE id > cursor ORDER BY id` queries (no `OFFSET` scan)
- **Natural fit**: UUIDv7 IDs are monotonically increasing, so `id` order matches creation order

## Request Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `cursor` | UUID | _(none)_ | ID of the last item from the previous page. Omit for the first page. |
| `limit` | int | 20 | Number of items per page. Clamped to `[1, 100]`. |

## Response Shape

All paginated endpoints return a `Page<T>` envelope:

```json
{
  "items": [ ... ],
  "nextCursor": "019...",
  "hasMore": true
}
```

| Field | Description |
|-------|-------------|
| `items` | The items in this page |
| `nextCursor` | The cursor to pass for the next page, or `null` if this is the last page |
| `hasMore` | `true` if more items exist beyond this page |

## Examples

### First page

```http
GET /api/properties?orgId=019...&limit=10
```

```json
{
  "items": [ { "id": "019-aaa...", "name": "Dishwasher" }, ... ],
  "nextCursor": "019-jjj...",
  "hasMore": true
}
```

### Next page

```http
GET /api/properties?orgId=019...&limit=10&cursor=019-jjj...
```

```json
{
  "items": [ { "id": "019-kkk...", "name": "Furnace" }, ... ],
  "nextCursor": null,
  "hasMore": false
}
```

### With search and filtering

Cursor pagination composes with search and filter parameters:

```http
GET /api/properties?orgId=019...&limit=10&search=kitchen&status=ACTIVE&cursor=019-jjj...
```

## Implementation

The `Page` record uses an "overfetch" strategy:

1. Query fetches `limit + 1` items
2. If `limit + 1` items are returned, `hasMore = true` and the extra item is trimmed
3. `nextCursor` is the ID of the last item in the trimmed list
4. If fewer than `limit + 1` items are returned, `hasMore = false` and `nextCursor = null`

This avoids a separate `COUNT(*)` query. See `Page.fromOverfetch()` in the domain model.
