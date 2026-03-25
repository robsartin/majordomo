-- V3: Update seed user password hash from BCrypt to Argon2id
UPDATE credentials
SET hashed_password = '$argon2id$v=19$m=16384,t=2,p=1$7ZDV8a9vfQ5iasgoHzUA7g$PUcR8b7h3I9Ti4sf+8tAfSxvr4+XLwyGF9dmso1eui0',
    updated_at = now()
WHERE user_id = '019606a0-0000-7000-8000-000000000001';
