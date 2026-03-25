-- V2: Seed default user Rob Sartin

INSERT INTO users (id, username, email, created_at, updated_at)
VALUES (
    '019606a0-0000-7000-8000-000000000001',
    'robsartin',
    'rob.sartin@gmail.com',
    now(),
    now()
);

INSERT INTO credentials (id, user_id, hashed_password, created_at, updated_at)
VALUES (
    '019606a0-0000-7000-8000-000000000002',
    '019606a0-0000-7000-8000-000000000001',
    '$2a$10$6t00FGN9bsAczx5/czYSnu2pHhqWycVfG5lNR2lURHGsH5RsdGX1q',
    now(),
    now()
);

INSERT INTO organizations (id, name, created_at, updated_at)
VALUES (
    '019606a0-0000-7000-8000-000000000003',
    'Personal',
    now(),
    now()
);

INSERT INTO memberships (id, user_id, organization_id, role, created_at, updated_at)
VALUES (
    '019606a0-0000-7000-8000-000000000004',
    '019606a0-0000-7000-8000-000000000001',
    '019606a0-0000-7000-8000-000000000003',
    'OWNER',
    now(),
    now()
);
