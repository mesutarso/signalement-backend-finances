alter table app.qualification add column missing_elements varchar(500);
alter table app.qualification add column dgi_reference varchar(80);

alter table app.qualification drop constraint qualification_decision_check;
alter table app.qualification drop constraint qualification_check;

alter table app.qualification add constraint qualification_decision_check
    check (decision in ('confirm', 'reject', 'wait'));

alter table app.qualification add constraint qualification_check check (
    (decision = 'wait'
        and finding = 'refusal'
        and proof = 'pending'
        and missing_elements is not null
        and dgi_reference is null
        and seller_name is null
        and seller_tax_id is null
        and issued_on is null
        and amount is null
        and document_number is null
        and read_code is null)
    or (decision = 'confirm'
        and finding = 'refusal'
        and proof = 'authority'
        and dgi_reference is not null
        and missing_elements is null
        and seller_name is null
        and seller_tax_id is null
        and issued_on is null
        and amount is null
        and document_number is null
        and read_code is null)
    or (decision = 'confirm'
        and dgi_reference is null
        and missing_elements is null
        and finding is not null
        and proof is not null
        and (
            (finding = 'non-normalized' and proof in ('photo', 'register'))
            or (finding in ('unknown-seller', 'fake-invoice', 'understated', 'missing-transmission') and proof = 'register')
        ))
    or (decision = 'reject'
        and finding is null
        and proof is null
        and missing_elements is null
        and seller_name is null
        and seller_tax_id is null
        and issued_on is null
        and amount is null
        and document_number is null
        and read_code is null)
);
