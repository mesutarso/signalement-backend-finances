create table app.rule_activation (
    id bigint generated always as identity primary key,
    rule_code varchar(32) not null references app.rule_version(code),
    activated_at timestamptz not null
);
create index rule_activation_date_idx on app.rule_activation (activated_at desc, id desc);
insert into app.rule_activation (rule_code, activated_at)
select code, '2026-10-01 00:00:00+01'::timestamptz from app.rule_version where code = 'cdc-2026-10-28';
insert into app.rule_activation (rule_code, activated_at)
select code, current_timestamp from app.rule_version where active and code <> 'cdc-2026-10-28';
