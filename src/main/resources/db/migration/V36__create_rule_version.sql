create table app.rule_version (
    id uuid primary key,
    code varchar(32) not null unique,
    fake_invoice integer not null,
    missing_transmission integer not null,
    understated integer not null,
    non_normalized integer not null,
    refusal integer not null,
    bonus integer not null,
    ceiling integer not null,
    daily_cap integer not null,
    spacing_days integer not null,
    active boolean not null,
    actor_id uuid,
    recorded_at timestamptz not null,
    check (fake_invoice >= 1 and missing_transmission >= 1 and understated >= 1 and non_normalized >= 1 and refusal >= 1),
    check (bonus in (0, 1)),
    check (ceiling >= 1 and daily_cap >= 1 and spacing_days >= 1)
);

create unique index rule_version_active_idx on app.rule_version (active) where active;

create table app.rule_tier (
    id uuid primary key,
    rule_id uuid not null references app.rule_version (id),
    threshold integer not null,
    amount integer not null,
    position integer not null,
    unique (rule_id, position),
    unique (rule_id, threshold),
    check (threshold >= 1 and amount >= 1 and position >= 1)
);

insert into app.rule_version (
    id, code, fake_invoice, missing_transmission, understated, non_normalized, refusal,
    bonus, ceiling, daily_cap, spacing_days, active, actor_id, recorded_at)
values (
    '6c0c2026-1028-4000-8000-000000000001',
    'cdc-2026-10-28',
    4, 3, 3, 2, 1,
    1, 1000000, 5, 3,
    true, null, timestamptz '2026-10-28 00:00:00+00');

insert into app.rule_tier (id, rule_id, threshold, amount, position) values
    (gen_random_uuid(), '6c0c2026-1028-4000-8000-000000000001', 30, 10000, 1),
    (gen_random_uuid(), '6c0c2026-1028-4000-8000-000000000001', 60, 20000, 2),
    (gen_random_uuid(), '6c0c2026-1028-4000-8000-000000000001', 100, 30000, 3);

do $$
declare
    granted integer := 60000;
    threshold integer := 100;
    position integer := 4;
begin
    while granted + 30000 <= 1000000 loop
        threshold := threshold + 50;
        insert into app.rule_tier (id, rule_id, threshold, amount, position)
        values (gen_random_uuid(), '6c0c2026-1028-4000-8000-000000000001', threshold, 30000, position);
        granted := granted + 30000;
        position := position + 1;
    end loop;
end $$;
