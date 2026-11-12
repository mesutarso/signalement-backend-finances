-- Keep the former decisions and rewards intact. Only undecided reports enter the new workflow.
alter table app.report add column validation_state varchar(16) not null default 'queued';
alter table app.report add constraint report_validation_state_check
    check (validation_state in ('queued', 'analyzing', 'doubt', 'validated', 'rejected', 'legacy'));
update app.report r set validation_state = 'legacy'
where exists (select 1 from app.qualification q where q.report_id = r.id)
   or exists (select 1 from app.point_movement m where m.report_id = r.id);
create index report_validation_queue_idx on app.report (validation_state, reported_at desc);

create table app.reward_setting (
    id uuid primary key,
    amount integer not null check (amount > 0),
    active boolean not null,
    actor_id uuid,
    recorded_at timestamptz not null
);
create unique index reward_setting_active_idx on app.reward_setting (active) where active;
insert into app.reward_setting values ('50002026-1112-4000-8000-000000000001', 5000, true, null, current_timestamp);

create table app.report_assessment (
    report_id uuid primary key references app.report(id),
    revision bigint not null default 0,
    generation bigint not null default 0,
    job_state varchar(16) not null default 'queued' check (job_state in ('queued', 'running', 'done')),
    attempts integer not null default 0 check (attempts >= 0),
    available_at timestamptz not null default current_timestamp,
    lease_until timestamptz,
    lease_id uuid,
    error_code varchar(80),
    reason varchar(1000) not null default 'Analyse des pièces en attente.',
    photo_id uuid references app.report_photo(id),
    seller_name varchar(160),
    seller_tax_id varchar(40),
    document_number varchar(80),
    document_date date,
    vat_status varchar(16) not null default 'unknown' check (vat_status in ('unknown', 'registered', 'unregistered')),
    vat_evidence varchar(500),
    vat_source varchar(16) check (vat_source in ('manual', 'dgi-rep')),
    checked_by uuid,
    checked_at timestamptz,
    normalization varchar(16) not null default 'unknown' check (normalization in ('unknown', 'normalized', 'non-normalized')),
    normalization_evidence varchar(500),
    purchase_conformity varchar(16) not null default 'unknown' check (purchase_conformity in ('unknown', 'conform', 'nonconform')),
    purchase_evidence varchar(500),
    vat_charged varchar(16) not null default 'unknown' check (vat_charged in ('unknown', 'yes', 'no')),
    vat_charge_evidence varchar(500),
    unique_invoice varchar(16) not null default 'unknown' check (unique_invoice in ('unknown', 'unique', 'duplicate')),
    uniqueness_evidence varchar(500),
    extracted jsonb not null default '{}'::jsonb,
    reading_ids jsonb not null default '[]'::jsonb,
    updated_at timestamptz not null default current_timestamp,
    check (vat_status = 'unknown' or (vat_evidence is not null and vat_source is not null and checked_by is not null and checked_at is not null))
);
create index assessment_jobs_idx on app.report_assessment (available_at, report_id) where job_state <> 'done';
create index assessment_lease_idx on app.report_assessment (lease_until) where job_state = 'running';
insert into app.report_assessment (report_id)
select id from app.report where validation_state <> 'legacy';

create table app.assessment_event (
    id uuid primary key,
    report_id uuid not null references app.report(id),
    revision bigint not null,
    origin varchar(16) not null check (origin in ('automatic', 'manual', 'correction')),
    actor_id uuid,
    decision varchar(16) not null,
    reason varchar(1000) not null,
    evidence jsonb not null,
    rule_version varchar(32) not null,
    recorded_at timestamptz not null,
    unique (report_id, revision)
);
create index assessment_event_report_idx on app.assessment_event (report_id, recorded_at desc);

-- Fixed rewards have one row per report, regardless of retries or subsequent reviews.
alter table app.prime add column report_id uuid references app.report(id);
alter table app.prime add column reward_setting_id uuid references app.reward_setting(id);
alter table app.prime add column invoice_key varchar(64);
alter table app.prime alter column phone drop not null;
alter table app.prime drop constraint prime_threshold_check;
alter table app.prime add constraint prime_threshold_check check (
    (report_id is null and threshold > 0 and reward_setting_id is null)
    or (report_id is not null and threshold = 0 and reward_setting_id is not null));
alter table app.prime drop constraint prime_declarant_id_rule_version_threshold_key;
create unique index prime_legacy_tier_idx on app.prime (declarant_id, rule_version, threshold) where report_id is null;
create unique index prime_report_reward_idx on app.prime (report_id) where report_id is not null;
create unique index prime_invoice_reward_idx on app.prime (invoice_key) where invoice_key is not null;
create index prime_setting_idx on app.prime (reward_setting_id) where reward_setting_id is not null;

alter table app.qualification drop constraint qualification_check;
alter table app.qualification add constraint qualification_check check (
    (decision = 'wait' and finding in ('refusal', 'verification') and proof = 'pending'
        and missing_elements is not null and dgi_reference is null)
    or (decision = 'confirm' and finding = 'refusal' and proof = 'authority'
        and dgi_reference is not null and missing_elements is null)
    or (decision = 'confirm' and finding <> 'refusal' and finding is not null
        and proof in ('photo', 'register') and missing_elements is null and dgi_reference is null)
    or (decision = 'reject' and finding is null and proof is null
        and missing_elements is null and seller_name is null and seller_tax_id is null
        and issued_on is null and amount is null and document_number is null and read_code is null)
);
