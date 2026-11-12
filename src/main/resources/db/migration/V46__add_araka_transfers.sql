-- Additive provider orders. Sandbox results never write to the financial payment ledger.
create table app.payment_transfer (
    id uuid primary key,
    request_id uuid not null unique,
    line_id uuid references app.payment_statement_line(id),
    environment varchar(16) not null check (environment in ('sandbox', 'production')),
    reference varchar(20) not null unique,
    provider varchar(16) not null check (provider in ('MPESA', 'AIRTEL', 'ORANGE', 'AFRIMONEY')),
    wallet varchar(14) not null,
    amount numeric(8,2) not null check (amount > 0),
    currency varchar(3) not null default 'CDF' check (currency = 'CDF'),
    state varchar(16) not null check (state in ('submitting', 'pending', 'paid', 'failed', 'unknown')),
    code varchar(64) not null,
    transaction_id varchar(180),
    actor_id uuid not null,
    started_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    next_check_at timestamptz not null default current_timestamp + interval '90 seconds',
    check_count integer not null default 0,
    lease_id uuid,
    lease_until timestamptz,
    check (line_id is not null or environment = 'sandbox')
);
create unique index payment_transfer_active_line_idx on app.payment_transfer (line_id, environment)
    where line_id is not null and state in ('submitting', 'pending', 'unknown', 'paid');
create index payment_transfer_reconcile_idx on app.payment_transfer (next_check_at)
    where state in ('submitting', 'pending', 'unknown');
create index payment_transfer_recent_idx on app.payment_transfer (started_at desc, id);
create index payment_transfer_line_idx on app.payment_transfer (line_id, started_at desc) where line_id is not null;

-- Former SIM records remain intact for audit, but cannot prevent or certify an actual payout.
drop index app.payment_attempt_paid_line_idx;
create unique index payment_attempt_paid_line_idx on app.payment_attempt (line_id)
    where result = 'paid' and reference_code not like 'SIM-%';
