create table app.user_account (
    id uuid primary key,
    username varchar(80) not null unique,
    password_hash varchar(120) not null,
    role varchar(32) not null,
    enabled boolean not null,
    failed_attempts integer not null,
    locked_until timestamptz,
    created_at timestamptz not null
);
