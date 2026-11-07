create table app.point_movement (
    id uuid primary key,
    report_id uuid not null references app.report (id),
    declarant_id varchar(80) not null,
    tax_id varchar(40) not null,
    finding varchar(32) not null,
    base_points integer not null,
    bonus_points integer not null,
    kind varchar(16) not null,
    rule_version varchar(32) not null,
    dgi_reference varchar(80),
    reason varchar(500),
    filed_on date not null,
    actor_id uuid not null,
    recorded_at timestamptz not null,
    check (kind in ('credit', 'reversal')),
    check (
        (kind = 'credit' and base_points > 0 and bonus_points in (0, 1) and dgi_reference is not null and reason is null)
        or (kind = 'reversal' and base_points < 0 and bonus_points in (-1, 0) and dgi_reference is null and reason is not null)
    )
);

create index point_movement_report_idx on app.point_movement (report_id, recorded_at desc);
create index point_movement_tax_idx on app.point_movement (tax_id, filed_on);
create index point_movement_declarant_idx on app.point_movement (declarant_id, filed_on);
