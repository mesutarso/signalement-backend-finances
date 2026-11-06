delete from app.report_photo
where report_id in (select id from app.report where source = 'fixture');

delete from app.report
where source = 'fixture';
