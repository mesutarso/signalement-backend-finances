create sequence app.payment_sample_seq;

create table app.payment_sample (
    id uuid primary key,
    reference_code varchar(32) not null unique,
    period_start date not null,
    period_end date not null,
    prepared_by uuid not null,
    prepared_at timestamptz not null,
    check (period_end >= period_start),
    unique (period_start, period_end)
);

create table app.payment_sample_line (
    id uuid primary key,
    sample_id uuid not null references app.payment_sample (id),
    attempt_id uuid not null references app.payment_attempt (id),
    reference_code varchar(80) not null,
    operator varchar(16) not null,
    amount integer not null,
    result varchar(16),
    reason varchar(500),
    reviewed_by uuid,
    reviewed_at timestamptz,
    check (operator in ('airtel', 'm-pesa', 'orange', 'afrimoney')),
    check (result is null or result in ('conform', 'anomaly')),
    check (
        (result is null and reason is null and reviewed_at is null and reviewed_by is null)
        or (result = 'conform' and reason is null and reviewed_at is not null and reviewed_by is not null)
        or (result = 'anomaly' and reason is not null and reviewed_at is not null and reviewed_by is not null)
    )
);
