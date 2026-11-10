-- Expand the accepted evidence for refusals. Existing decisions remain unchanged.
alter table app.qualification drop constraint qualification_check;
alter table app.qualification add constraint qualification_check check (
    (decision = 'wait' and finding = 'refusal' and proof = 'pending'
        and missing_elements is not null and dgi_reference is null)
    or (decision = 'confirm' and finding = 'refusal' and proof = 'authority'
        and dgi_reference is not null and missing_elements is null)
    or (decision = 'confirm' and finding <> 'refusal'
        and finding is not null and proof in ('photo', 'register')
        and missing_elements is null and dgi_reference is null)
    or (decision = 'reject' and finding is null and proof is null
        and missing_elements is null and seller_name is null and seller_tax_id is null
        and issued_on is null and amount is null and document_number is null and read_code is null)
);
