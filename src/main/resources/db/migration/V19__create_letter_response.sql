create table app.letter_response (
    id uuid primary key,
    letter_id uuid not null references app.letter (id),
    received_on date not null,
    service varchar(160) not null,
    reference_code varchar(80) not null,
    conclusion varchar(500) not null,
    content bytea not null,
    content_type varchar(80) not null,
    fingerprint varchar(64) not null,
    actor_id uuid not null,
    recorded_at timestamptz not null,
    unique (letter_id, reference_code)
);

create index letter_response_letter_idx on app.letter_response (letter_id, recorded_at desc);
