# Majordomo

A service-based personal information and property management system. Majordomo acts as the head of household — it knows where everything is, who sold it, and who is supposed to fix it.

## Vision

Majordomo bridges the gap between digital data (PIM) and physical assets (property management) through a modular, service-oriented architecture. Rather than treating belongings as static inventory, Majordomo orchestrates the relationships between people, things, and time.

An item isn't just a "thing you own" — it's a nexus of relationships: the vendor who sold it, the technician who maintains it, and the future you who needs it to work.

## Architecture

Majordomo is built as a collection of independent services, each named after a role within a traditional estate household:

| Service | Role | Responsibility |
|---------|------|----------------|
| **The Steward** | Property Service | Manages physical assets, their current state, and documentation (manuals, receipts) |
| **The Concierge** | Contact Service | Manages relationships — vendors, maintenance professionals, and sellers |
| **The Herald** | Calendar/Notification Service | Handles scheduling — service dates, reminders, warranty expirations |
| **The Ledger** | Finance Service | Tracks costs from purchase price to lifetime maintenance spend |

The **Majordomo** itself is the orchestration layer that ties these services together.

Additional services can be introduced over time (e.g., "The Gardener" for landscaping, "The Archivist" for digital documents) without breaking the naming or architectural model.

## Key Concepts

- **Service-based**: Each domain is an independent service that can be developed, deployed, and scaled on its own
- **Relationship-first**: The system models connections between people, assets, and events — not just static records
- **Lifecycle-aware**: Assets are tracked from acquisition through maintenance to eventual replacement

## Status

Early development. Architecture decisions are being recorded in `doc/adr/`.

## License

See [LICENSE](LICENSE).
