CREATE TABLE user_preferences (
    id                              UUID PRIMARY KEY,
    user_id                         UUID         NOT NULL UNIQUE REFERENCES users(id),
    notification_email              VARCHAR(255),
    notifications_enabled           BOOLEAN      NOT NULL DEFAULT true,
    notification_categories_disabled TEXT[]       DEFAULT '{}',
    timezone                        VARCHAR(100) DEFAULT 'UTC',
    locale                          VARCHAR(20)  DEFAULT 'en',
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
