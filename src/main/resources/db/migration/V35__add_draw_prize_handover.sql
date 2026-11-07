alter table app.draw_prize add column handed_at timestamptz;
alter table app.draw_prize add column handed_by uuid;
alter table app.draw_prize add column note varchar(500);

alter table app.draw_prize add constraint draw_prize_handover_check check (
    (handed_at is null and handed_by is null)
    or (handed_at is not null and handed_by is not null)
);
