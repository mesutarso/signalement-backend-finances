-- Accounts are linked only after a documented human identity check, never by phone alone.
create table app.beneficiary_account (
    declarant_id varchar(80) primary key,
    beneficiary_key varchar(64) not null,
    evidence_reference varchar(160) not null,
    verified_by uuid not null,
    verified_at timestamptz not null
);
create index beneficiary_account_group_idx on app.beneficiary_account (beneficiary_key);
create index prime_beneficiary_status_idx on app.prime (declarant_id, status);
