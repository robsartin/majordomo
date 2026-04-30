# Ledger

The **Ledger** is read-only — it doesn't track its own data. Instead it
derives spend totals from your properties' purchase prices and your
schedules' service records.

## The dashboard

`/ledger` shows:

- **Total spend** — purchase price + maintenance to date for every
  active property in your org.
- **Purchase price** — sum of all `purchasePrice` values you've entered
  on properties.
- **Maintenance to date** — sum of every service record's `cost`.
- **Projected annual** — based on each schedule's frequency and
  `estimatedCost`, what you'd spend over the next 12 months at the
  current cadence.

Below the cards, a **Spend by property** table breaks down each
property's purchase price, maintenance to date, and total — sorted by
total spend descending. Click a property name to jump to its detail.

## What's counted

- **Purchase price** comes from the Properties form. If you haven't
  entered one, that property contributes `$0` to the purchase column.
- **Maintenance** is summed from `ServiceRecord.cost` across every
  schedule under the property. Service records you log without a cost
  contribute `$0`.
- **Archived properties** are excluded.
- **Cross-org isolation**: only your organization's properties roll up.

## What's NOT counted

- Estimated costs that haven't actually been performed (those feed
  *projected* annual, not totals).
- Income, refunds, sale prices, or anything outside the purchase /
  maintenance dimensions.
- Tax — Majordomo doesn't try to be a tax tool.

## Same data via REST

If you need raw numbers programmatically, `/api/ledger/properties/{id}/spend`,
`/api/ledger/organizations/{id}/spend`, and
`/api/ledger/organizations/{id}/projected-annual` return JSON with the
same data the page shows.
