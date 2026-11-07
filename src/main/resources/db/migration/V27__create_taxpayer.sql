create table app.taxpayer (
    id uuid primary key,
    tax_id varchar(40) not null,
    name varchar(160),
    verified boolean not null,
    verified_at timestamptz,
    recorded_at timestamptz not null,
    check ((verified = false and verified_at is null) or (verified = true and verified_at is not null))
);

create unique index taxpayer_tax_id_idx on app.taxpayer (lower(btrim(tax_id)));
