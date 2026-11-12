-- Preserve drafts. Sending remains an explicit action, never an automatic backfill.
alter table app.declarant_message drop constraint declarant_message_status_check;
alter table app.declarant_message add constraint declarant_message_status_check
    check (status in ('prepared', 'sending', 'accepted', 'failed', 'unknown'));
alter table app.declarant_message add column send_attempt_id uuid;
alter table app.declarant_message add column recipient varchar(40);
alter table app.declarant_message add column send_started_at timestamptz;
alter table app.declarant_message add column sent_at timestamptz;
alter table app.declarant_message add column delivery_code varchar(64);
alter table app.declarant_message add column provider_status integer;
