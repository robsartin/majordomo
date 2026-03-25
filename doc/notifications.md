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
