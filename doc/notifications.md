# Notifications

Majordomo sends notifications for maintenance reminders and warranty expirations. Notifications are delivered via email and protected by Resilience4j circuit breaker and retry patterns.

## Notification Categories

| Category | Trigger | Description |
|----------|---------|-------------|
| `MAINTENANCE_DUE` | Scheduled maintenance approaching `next_due` date | Reminds the user to schedule or perform maintenance |
| `WARRANTY_EXPIRING` | Property `warranty_expires_on` approaching | Alerts the user before warranty coverage lapses |
| `SITE_UPDATES` | System announcements | New features, maintenance windows, etc. |

## User Preferences

Each user can configure notification preferences via `UserPreferences`:

- **`notifications_enabled`** — master toggle; if `false`, no notifications are sent
- **`notification_categories_disabled`** — list of category names to suppress (e.g., `["SITE_UPDATES"]`)
- **`notification_email`** — override email for notifications (defaults to the user's primary email)
- **`timezone`** — used for scheduling notification delivery windows
- **`locale`** — used for message localization

### API

```http
GET  /api/users/{userId}/preferences
PUT  /api/users/{userId}/preferences
Content-Type: application/json

{
  "notificationsEnabled": true,
  "notificationCategoriesDisabled": ["SITE_UPDATES"],
  "notificationEmail": "alerts@example.com",
  "timezone": "America/New_York",
  "locale": "en-US"
}
```

## Deduplication

To avoid sending duplicate notifications:

- **Maintenance reminders**: `MaintenanceSchedule.notification_sent_at` is set when a reminder is sent. Cleared when the schedule advances to the next due date.
- **Warranty alerts**: `Property.warranty_notification_sent_at` is set when a warranty expiration alert is sent.

## SMTP Configuration

The notification adapter currently logs notifications via SLF4J. To enable real email delivery, configure Spring Mail in `application.yml`:

```yaml
spring:
  mail:
    host: smtp.example.com
    port: 587
    username: notifications@example.com
    password: ${SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

Then update `NotificationAdapter` to delegate to `JavaMailSender`.

## Resilience

The `NotificationAdapter` is protected by Resilience4j:

- **Circuit breaker** (`notification`): Opens after repeated failures to prevent cascading issues with the mail server. Falls back to logging a warning.
- **Retry** (`notification`): Retries transient failures before triggering the circuit breaker.

Configuration is in `application.yml` under `resilience4j:`.

## Scheduled Jobs

The Herald runs three scheduled notification jobs. All crons are configurable under
`majordomo.notifications` in `application.yml`.

| Job | Class | Default cron | What it sends |
|-----|-------|--------------|---------------|
| Maintenance reminders | `MaintenanceNotificationService` | `cron` = `0 0 8 * * *` | One email per maintenance schedule due within 7 days, once per due date (deduplicated). |
| Warranty alerts | `WarrantyAlertService` | `warranty-cron` = `0 0 9 * * *` | One email per property whose warranty expires within 30 days, once per property (deduplicated). |
| Weekly digest | `WeeklyDigestService` | `digest-cron` = `0 0 8 * * MON` | **One** rollup email per admin/owner summarizing everything due in the next 30 days. |

### Weekly maintenance digest

`WeeklyDigestService` assembles, per user, a single weekly email that rolls up
everything coming due in the next **30 days** — both upcoming maintenance and
property warranty expirations — so a user has one place to plan the week/month.

Design notes:

- **Reuses `HeraldCalendarService`** (the calendar-feed assembler) for both event
  collection and per-user category preference filtering. A user who has disabled
  `MAINTENANCE_DUE` sees only warranties; one who has disabled both — or disabled
  notifications entirely — assembles to nothing.
- **An empty digest is never sent.** A user with nothing upcoming (or who has opted
  out of everything) receives no email at all.
- **One email per user.** Events across all of the user's organizations are combined
  into a single digest, sorted by date.
- **Recipients** are organization owners and admins (plain `MEMBER` roles are not
  notified, consistent with the per-event reminder jobs).
- **Send path** is the same Resilience4j-protected `NotificationPort`, so digests
  inherit the circuit-breaker/retry behavior above.

Unlike the per-event reminders, the digest is a stateless rollup: it does **not**
set or read `notification_sent_at`, so an item can appear in a weekly digest and
still trigger its own dated reminder.
