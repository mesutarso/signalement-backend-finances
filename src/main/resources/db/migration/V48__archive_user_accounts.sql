-- Additive migration: preserve accounts and their references in the immutable audit journal.
alter table app.user_account add column deleted_at timestamptz;

create index user_account_active_username_idx on app.user_account (username) where deleted_at is null;
