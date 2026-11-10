create table app.campaign_budget (
    id uuid primary key,
    amount bigint not null check (amount > 0),
    reference varchar(160) not null,
    actor_id uuid not null,
    recorded_at timestamptz not null
);
create index campaign_budget_date_idx on app.campaign_budget (recorded_at desc, id desc);
create table app.campaign_report (
    id uuid primary key,
    kind varchar(16) not null check (kind in ('weekly', 'monthly')),
    period_start date not null,
    period_end date not null,
    figures jsonb not null,
    budget bigint,
    actor_id uuid not null,
    generated_at timestamptz not null,
    unique (kind, period_start)
);
