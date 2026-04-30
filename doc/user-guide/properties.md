# Properties

Properties are the things you own — houses, vehicles, appliances,
tools, anything you might want to track over time. The **Steward**
service manages them.

## The list page

`/properties` shows every non-archived property in your organization.

- **Filter strip**: free-text search (matches name + description) and
  an exact-match category dropdown.
- **+ New property** button (top right): opens the create form.
- Click a property's name to jump to its detail page.

## Adding a property

Click **+ New property**. The form has:

| Field | Notes |
|---|---|
| **Name** | Required. Short label (e.g. "Beach House", "Honda Civic 2020"). |
| **Category** | Free-text grouping (e.g. "vacation", "vehicle"). Becomes a filter on the list page. |
| **Description** | Long-form notes. |
| **Address / location** | Where the property lives. |
| **Purchase price** | Decimal value. Used by the [Ledger](ledger.md). Must be non-negative. |
| **Parent property** | Optional dropdown. Lets you nest sub-properties (e.g. "Boiler" inside "Vacation House"). The picker excludes the property being edited and any of its existing descendants to prevent cycles. |

On save you land on the new property's detail page.

## The detail page

`/properties/{id}` shows everything that touches a property:

- **Header**: name, status badge, vCard-style download? (no — vCard is
  for contacts), and an **Edit** button to re-open the form.
- **Parent / Children panel**: navigation up and down the property
  tree.
- **Linked contacts**: vendors / installers / manufacturers associated
  via [Property↔Contact links](#linking-contacts).
- **Schedules**: maintenance schedules with a days-until-due delta
  (negative = overdue). "+ Add schedule" link drops you into the
  [Herald](schedules.md) form pre-filled with this property.
- **Recent service records**: most recent 10 service events.
- **Attachments**: file uploads (manuals, receipts, photos).

## Linking contacts

To associate a contact (vendor, installer, etc.) with this property:

1. Scroll to **Linked contacts**.
2. Below the table, pick a contact from the dropdown, choose a role
   (`VENDOR`, `SERVICE_PROVIDER`, `MANUFACTURER`, `INSTALLER`, `OTHER`),
   add optional notes, click **+ Link contact**.
3. The contact appears in the table immediately.

The dropdown only shows contacts already in your org and not yet linked
to this property. To create a new contact first, see
[Contacts](contacts.md).

To **unlink**, click the small "Unlink" button at the right end of the
row. The link is soft-deleted; the contact itself stays untouched.

## Editing or archiving

- Click **Edit** on the detail page to reopen the form.
- Archiving today is REST-only (`DELETE /api/properties/{id}` soft-deletes
  by setting `archivedAt`). A web "Archive" button is on the roadmap.
