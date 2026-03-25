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
    '$argon2id$v=19$m=16384,t=2,p=1$7ZDV8a9vfQ5iasgoHzUA7g$PUcR8b7h3I9Ti4sf+8tAfSxvr4+XLwyGF9dmso1eui0',
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
