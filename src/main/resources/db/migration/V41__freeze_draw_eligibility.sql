create table app.draw_entry (
    draw_id uuid not null references app.campaign_draw(id),
    ticket_id uuid not null references app.draw_ticket(id),
    position integer not null,
    person_key varchar(100) not null,
    primary key(draw_id, ticket_id),
    unique(draw_id, position)
);
