create sequence app.campaign_draw_seq;

create table app.campaign_draw (
    id uuid primary key,
    reference_code varchar(32) not null unique,
    kind varchar(16) not null,
    period_start date not null,
    witness varchar(160) not null,
    seed bigint not null,
    status varchar(16) not null,
    drawn_by uuid not null,
    drawn_at timestamptz not null,
    published_by uuid,
    published_at timestamptz,
    check (kind in ('monthly', 'final')),
    check (extract(day from period_start) = 1),
    check (status in ('drawn', 'published')),
    check (
        (status = 'drawn' and published_by is null and published_at is null)
        or (status = 'published' and published_by is not null and published_at is not null)
    ),
    unique (kind, period_start)
);

create unique index campaign_draw_final_idx on app.campaign_draw (kind) where kind = 'final';

create table app.draw_prize (
    id uuid primary key,
    draw_id uuid not null references app.campaign_draw (id),
    ticket_id uuid not null references app.draw_ticket (id),
    report_reference varchar(80) not null,
    label varchar(80) not null,
    unique (draw_id, ticket_id)
);
