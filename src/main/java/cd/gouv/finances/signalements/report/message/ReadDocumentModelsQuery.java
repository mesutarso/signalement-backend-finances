package cd.gouv.finances.signalements.report.message;

import cd.gouv.finances.signalements.report.dto.DocumentModelResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadDocumentModelsQuery {

    private final JdbcTemplate jdbc;

    public ReadDocumentModelsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAuthority('template.read')")
    @Transactional(readOnly = true)
    public DocumentModelResponse execute() {
        var models = jdbc.query("""
                select kind, body, recorded_at, actor_id
                from app.document_model
                where active = true and kind <> 'photo'
                order by kind
                """, (row, number) -> new DocumentModelResponse.Item(
                row.getString(1),
                DeclarantMessageText.effectivePattern(row.getString(1), row.getString(2), row.getObject(4) == null),
                row.getTimestamp(3).toInstant()));
        return new DocumentModelResponse(models, DeclarantMessageText.SMS_LIMIT, referenceLength());
    }

    public int referenceLength() {
        // Java and the editor count UTF-16 units consistently, including supplementary characters.
        var lengths = jdbc.query("select reference_code from app.report union select report_reference from app.declarant_message",
                (row, n) -> row.getString(1).length());
        return Math.max(DeclarantMessageText.REFERENCE_RESERVE,
                lengths.stream().mapToInt(Integer::intValue).max().orElse(DeclarantMessageText.REFERENCE_RESERVE));
    }
}
