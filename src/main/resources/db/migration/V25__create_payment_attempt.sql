create table app.payment_attempt (
    id uuid primary key,
    statement_id uuid not null references app.payment_statement (id),
    line_id uuid not null references app.payment_statement_line (id),
    result varchar(16) not null,
    reference_code varchar(80),
    reason varchar(500),
    actor_id uuid not null,
    recorded_at timestamptz not null,
    check (result in ('paid', 'failed', 'unknown')),
    check (
        (result = 'paid' and reference_code is not null and reason is null)
        or (result = 'failed' and reason is not null)
        or (result = 'unknown' and reason is not null)
    )
);

create unique index payment_attempt_paid_line_idx on app.payment_attempt (line_id) where result = 'paid';
