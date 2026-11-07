create table app.payout_number (
    id uuid primary key,
    declarant_id varchar(80) not null unique,
    phone varchar(40),
    state varchar(16) not null,
    actor_id uuid not null,
    recorded_at timestamptz not null,
    updated_at timestamptz not null,
    check (state in ('ready', 'missing', 'several')),
    check (
        (state = 'ready' and phone is not null)
        or (state in ('missing', 'several') and phone is null)
    )
);

create index payout_number_updated_idx on app.payout_number (updated_at desc);
