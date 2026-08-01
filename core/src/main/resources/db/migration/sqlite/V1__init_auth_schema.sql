CREATE TABLE IF NOT EXISTS auth_users (
    uuid VARCHAR(36) PRIMARY KEY NOT NULL,
    username VARCHAR(16) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    hashed_ip VARCHAR(64) NOT NULL,
    registration_date TIMESTAMP NOT NULL,
    last_login_date TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_users_username ON auth_users(username);
