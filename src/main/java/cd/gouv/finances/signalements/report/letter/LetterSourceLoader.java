package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.letter.LetterContent.Photo;
import cd.gouv.finances.signalements.report.letter.LetterContent.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LetterSourceLoader {

    private final JdbcTemplate jdbc;

    public LetterSourceLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Source> findings() {
        return jdbc.query("""
                select report.id, report.reference_code,
                       qualification.seller_tax_id, qualification.seller_name,
                       qualification.decision, qualification.finding, qualification.proof,
                       qualification.document_number, qualification.issued_on, qualification.amount,
                       qualification.justification, qualification.missing_elements, qualification.dgi_reference
                from (
                    select distinct on (report_id) *
                    from app.qualification
                    order by report_id, recorded_at desc
                ) qualification
                join app.report report on report.id = qualification.report_id
                where qualification.decision in ('confirm', 'wait')
                  and nullif(trim(qualification.seller_tax_id), '') is not null
                  and not exists (
                    select 1 from app.letter_line line join app.letter letter on letter.id = line.letter_id
                    where line.report_id = report.id and letter.status = 'validated'
                      and letter.prepared_at >= qualification.recorded_at)
                order by lower(trim(qualification.seller_tax_id)), report.reference_code
                """, (row, number) -> new Source(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getString(3),
                row.getString(4),
                row.getString(5),
                row.getString(6),
                row.getString(7),
                row.getString(8),
                row.getDate(9) == null ? null : row.getDate(9).toLocalDate(),
                row.getString(10),
                row.getString(11),
                row.getString(12),
                row.getString(13)));
    }

    public List<Photo> photos(List<UUID> reportIds) {
        if (reportIds.isEmpty()) {
            return List.of();
        }
        var marks = String.join(",", reportIds.stream().map(id -> "?").toList());
        var photos = new ArrayList<Photo>();
        jdbc.query(
                "select report_id, id, content_type from app.report_photo where report_id in (%s)".formatted(marks),
                (row, number) -> photos.add(new Photo(row.getObject(1, UUID.class), row.getObject(2, UUID.class), row.getString(3))),
                reportIds.toArray());
        return photos;
    }
}
