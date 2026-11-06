create table app.document_reading (
    id uuid primary key,
    document_reference varchar(200) not null,
    fingerprint varchar(64) not null,
    status varchar(16) not null,
    quality varchar(16),
    model varchar(64),
    schema_version varchar(32) not null,
    error_code varchar(64),
    created_at timestamptz not null
);

create index document_reading_reference_fingerprint_idx
    on app.document_reading (document_reference, fingerprint);

create table app.extracted_field (
    id uuid primary key,
    reading_id uuid not null references app.document_reading (id),
    name varchar(64) not null,
    value text,
    present boolean not null
);
