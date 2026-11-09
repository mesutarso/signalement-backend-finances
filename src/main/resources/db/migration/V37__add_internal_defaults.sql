alter table app.payout_number add column lost_at timestamptz;

alter table app.reexamination add column on_time boolean not null default false;

alter table app.reexamination alter column on_time drop default;

create table app.document_model (
    id uuid primary key,
    kind varchar(16) not null,
    body varchar(500) not null,
    active boolean not null,
    actor_id uuid,
    recorded_at timestamptz not null,
    check (kind in ('photo', 'payout', 'reexamination', 'situation')),
    check (length(body) - length(replace(body, '%s', '')) = 2)
);

create unique index document_model_active_idx on app.document_model (kind) where active;

insert into app.document_model (id, kind, body, active, actor_id, recorded_at) values
    ('6c0c2026-1107-4000-8000-000000000011', 'photo', 'Le signalement %s attend une nouvelle photo.', true, null, '2026-11-07 00:00:00+00'),
    ('6c0c2026-1107-4000-8000-000000000012', 'payout', 'Le signalement %s attend un numéro de paiement.', true, null, '2026-11-07 00:00:00+00'),
    ('6c0c2026-1107-4000-8000-000000000013', 'reexamination', 'La demande de réexamen du signalement %s a été traitée.', true, null, '2026-11-07 00:00:00+00'),
    ('6c0c2026-1107-4000-8000-000000000014', 'situation', 'La situation du signalement %s a été mise à jour.', true, null, '2026-11-07 00:00:00+00');
