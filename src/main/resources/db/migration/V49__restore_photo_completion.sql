-- Additional request-specific delivery, preserving existing messages and requests.
alter table app.declarant_message add column photo_request_id uuid references app.photo_request(id);
alter table app.declarant_message add constraint declarant_message_photo_request_check
    check (photo_request_id is null or kind = 'photo');
alter table app.declarant_message drop constraint declarant_message_report_id_kind_key;
create unique index declarant_message_report_kind_idx
    on app.declarant_message(report_id, kind) where photo_request_id is null;
create unique index declarant_message_photo_request_idx
    on app.declarant_message(photo_request_id) where photo_request_id is not null;
create index photo_request_open_report_idx on app.photo_request(report_id) where status = 'open';
create index report_assessment_nif_idx on app.report_assessment(lower(seller_tax_id));
create index report_assessment_number_idx on app.report_assessment(lower(document_number));

alter table app.report_photo add column replacement_request_id uuid references app.photo_request(id);
create index report_photo_replacement_request_idx on app.report_photo(replacement_request_id)
    where replacement_request_id is not null;
