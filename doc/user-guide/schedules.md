# Schedules

The **Herald** service tracks recurring maintenance — anything you want
to be reminded about on a cadence.

## The list page

`/schedules` lists every active maintenance schedule for your org,
sorted by `nextDue` ascending (soonest first). The columns:

- **Description** (e.g. "HVAC service", "Oil change")
- **Property** — links back to the property's detail page
- **Frequency** — `WEEKLY`, `MONTHLY`, `QUARTERLY`, `BIANNUAL`, `ANNUAL`,
  `CUSTOM` (with `customIntervalDays`)
- **Next due** — date plus days-until-due delta. Negative = overdue.

A filter strip narrows by property and by overdue-only.

## Adding a schedule

Click **+ New schedule** (top right) or use the per-property "+ Add
schedule" link from a property's detail page (which pre-fills the
property dropdown).

| Field | Notes |
|---|---|
| **Description** | Required. What needs doing. |
| **Property** | Required dropdown. |
| **Contact** | Optional — the vendor who performs the work. |
| **Frequency** | Required enum. Pick `CUSTOM` to expose `customIntervalDays`. |
| **Next due** | Required date. Future or past — overdue schedules are tracked too. |
| **Estimated cost** | Optional decimal. Used by the Ledger's projected-annual computation. |

## The detail page

`/schedules/{id}` shows the schedule plus a chronological list of
service records (newest first).

## Recording a service event

On the schedule detail page, the **+ Record service** form takes:

| Field | Notes |
|---|---|
| **Performed on** | Required date — when the work was done. |
| **Description** | Required — short summary. |
| **Cost** | Optional decimal. Feeds the Ledger. |
| **Notes** | Optional. |

On save, the schedule's `nextDue` rolls forward by its frequency and
the new service record appears in the history. The
`MAINTENANCE_DUE` notification (if you have it enabled) clears for
this cycle.

## Notifications

Maintenance reminders are emailed when a schedule's `nextDue` is
within the configured lead window. Per-user preferences let you mute
the `MAINTENANCE_DUE`, `WARRANTY_EXPIRING`, and `SITE_UPDATES`
categories independently — see your account preferences.
