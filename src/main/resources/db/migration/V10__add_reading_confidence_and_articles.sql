alter table app.document_reading add column confidence numeric(4, 3);

create table app.reading_article (
    id uuid primary key,
    reading_id uuid not null references app.document_reading (id),
    position integer not null,
    designation varchar(400),
    amount varchar(64)
);

create index reading_article_reading_idx on app.reading_article (reading_id, position);
