create sequence app.payment_simulation_seq;

alter table app.payment_attempt
    add column operator varchar(16) not null,
    add constraint payment_attempt_operator_check check (operator in ('airtel', 'm-pesa', 'orange', 'afrimoney'));
