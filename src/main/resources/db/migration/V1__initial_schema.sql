-- V1: Initial schema for Majordomo

-- ── Identity ──

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    username        VARCHAR(255) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at     TIMESTAMPTZ
);

CREATE TABLE credentials (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL UNIQUE REFERENCES users(id),
    hashed_password VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at     TIMESTAMPTZ
);

CREATE TABLE organizations (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at     TIMESTAMPTZ
);

CREATE TABLE memberships (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id),
    organization_id UUID         NOT NULL REFERENCES organizations(id),
    role            VARCHAR(50)  NOT NULL DEFAULT 'MEMBER',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at     TIMESTAMPTZ,
    UNIQUE (user_id, organization_id)
);

-- ── The Concierge (Contacts) ──

CREATE TABLE contacts (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations(id),
    formatted_name  VARCHAR(255) NOT NULL,
    family_name     VARCHAR(255),
    given_name      VARCHAR(255),
    nicknames       TEXT[]       DEFAULT '{}',
    emails          TEXT[]       DEFAULT '{}',
    telephones      TEXT[]       DEFAULT '{}',
    urls            TEXT[]       DEFAULT '{}',
    organization    VARCHAR(255),
    title           VARCHAR(255),
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at     TIMESTAMPTZ
);

CREATE TABLE addresses (
    id              UUID PRIMARY KEY,
    contact_id      UUID         NOT NULL REFERENCES contacts(id),
    label           VARCHAR(100),
    street          VARCHAR(255),
    city            VARCHAR(255),
    state           VARCHAR(255),
    postal_code     VARCHAR(50),
    country         VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at     TIMESTAMPTZ
);

-- ── The Steward (Property) ──

CREATE TABLE properties (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organizations(id),
    parent_id           UUID         REFERENCES properties(id),
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    serial_number       VARCHAR(255),
    model_number        VARCHAR(255),
    manufacturer        VARCHAR(255),
    category            VARCHAR(255),
    location            VARCHAR(255),
    status              VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    acquired_on         DATE,
    warranty_expires_on DATE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at         TIMESTAMPTZ
);

-- ── Property-Contact Relationships ──

CREATE TABLE property_contacts (
    id              UUID PRIMARY KEY,
    property_id     UUID         NOT NULL REFERENCES properties(id),
    contact_id      UUID         NOT NULL REFERENCES contacts(id),
    role            VARCHAR(50)  NOT NULL,
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at     TIMESTAMPTZ,
    UNIQUE (property_id, contact_id, role)
);

-- ── The Herald (Scheduling) ──

CREATE TABLE maintenance_schedules (
    id                   UUID PRIMARY KEY,
    property_id          UUID         NOT NULL REFERENCES properties(id),
    contact_id           UUID         REFERENCES contacts(id),
    description          VARCHAR(255) NOT NULL,
    frequency            VARCHAR(50)  NOT NULL,
    custom_interval_days INT,
    next_due             DATE         NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at          TIMESTAMPTZ
);

CREATE TABLE service_records (
    id              UUID PRIMARY KEY,
    property_id     UUID         NOT NULL REFERENCES properties(id),
    contact_id      UUID         REFERENCES contacts(id),
    schedule_id     UUID         REFERENCES maintenance_schedules(id),
    performed_on    DATE         NOT NULL,
    description     VARCHAR(255) NOT NULL,
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at     TIMESTAMPTZ
);

-- ── Indexes ──

CREATE INDEX idx_memberships_user_id ON memberships(user_id);
CREATE INDEX idx_memberships_organization_id ON memberships(organization_id);
CREATE INDEX idx_contacts_organization_id ON contacts(organization_id);
CREATE INDEX idx_addresses_contact_id ON addresses(contact_id);
CREATE INDEX idx_properties_organization_id ON properties(organization_id);
CREATE INDEX idx_properties_parent_id ON properties(parent_id);
CREATE INDEX idx_property_contacts_property_id ON property_contacts(property_id);
CREATE INDEX idx_property_contacts_contact_id ON property_contacts(contact_id);
CREATE INDEX idx_maintenance_schedules_property_id ON maintenance_schedules(property_id);
CREATE INDEX idx_maintenance_schedules_next_due ON maintenance_schedules(next_due);
CREATE INDEX idx_service_records_property_id ON service_records(property_id);
CREATE INDEX idx_service_records_performed_on ON service_records(performed_on);
