alter table app.report_assessment drop constraint report_assessment_vat_source_check;
alter table app.report_assessment add constraint report_assessment_vat_source_check
    check (vat_source in ('manual', 'dgi-rep', 'ocr'));
