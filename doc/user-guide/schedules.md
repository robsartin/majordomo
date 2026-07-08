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

### Quick "mark serviced" from the dashboard

The dashboard's **Upcoming Maintenance** panel (items due in the next 30
days, soonest first) has a **Mark serviced** button on each row. Clicking
it — after a confirmation — records a service performed today and rolls the
schedule's `nextDue` forward by its frequency, without leaving the
dashboard. Use it for the common case of "did it, log it"; use the detail
form when you need to set the date, cost, or notes.

## Notifications

Maintenance reminders are emailed when a schedule's `nextDue` is
within the configured lead window. Per-user preferences let you mute
the `MAINTENANCE_DUE`, `WARRANTY_EXPIRING`, and `SITE_UPDATES`
categories independently — see your account preferences.

### Weekly digest

Once a week (Mondays by default), Majordomo sends owners and admins a
single **digest email** rolling up everything due in the next 30 days —
upcoming maintenance *and* property warranty expirations — sorted by date,
so you have one place to plan the month. It's separate from the dated
reminders above: an item can show up in your Monday digest and still get
its own reminder closer to the date.

The digest honours the same preferences: mute `MAINTENANCE_DUE` or
`WARRANTY_EXPIRING` and those items drop out of the rollup. If you've muted
both categories (or turned notifications off entirely), or simply have
nothing coming up, **no digest is sent** — the weekly email only arrives
when there's something to plan for.

## Subscribe to a calendar feed

Prefer to see due dates in your own calendar app? Open **Calendar feed**
from the header (or `/account/calendar`) and click **Create feed URL**.
Copy the URL it shows — it's shown only once — and add it as a
*subscription* in Apple Calendar, Google Calendar, or Outlook.

The feed publishes your organization's upcoming maintenance due dates and
property warranty expirations as all-day events (each with a one-day-ahead
alarm), and it honours your notification-category preferences: mute
`MAINTENANCE_DUE` or `WARRANTY_EXPIRING` and those events drop out of the
feed. The URL contains a private token, so treat it like a password — anyone
with it can read your dates. **Revoke** a feed at any time from the same page;
subscribed calendars then stop updating.
