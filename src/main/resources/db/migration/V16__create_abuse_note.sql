create table app.abuse_note (
    id uuid primary key,
    fingerprint varchar(64) not null,
    decision varchar(16) not null,
    reason varchar(500) not null,
    actor_id uuid not null,
    recorded_at timestamptz not null,
    check (decision in ('noted', 'dismissed'))
);

create index abuse_note_fingerprint_idx on app.abuse_note (fingerprint, recorded_at desc);
