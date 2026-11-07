alter table app.prime drop constraint prime_status_check;

alter table app.prime add constraint prime_status_check check (status in ('proposed', 'approved', 'returned', 'withdrawn'));

create table app.prime_review (
    id uuid primary key,
    prime_id uuid not null references app.prime (id),
    result varchar(16) not null,
    reason varchar(500),
    actor_id uuid not null,
    recorded_at timestamptz not null,
    check (result in ('approved', 'returned')),
    check (
        (result = 'approved' and reason is null)
        or (result = 'returned' and reason is not null)
    )
);

create index prime_review_prime_idx on app.prime_review (prime_id, recorded_at desc);
