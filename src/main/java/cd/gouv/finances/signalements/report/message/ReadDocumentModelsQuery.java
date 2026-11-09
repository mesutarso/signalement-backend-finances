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

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public DocumentModelResponse execute() {
        var models = jdbc.query("""
                select kind, body, recorded_at
                from app.document_model
                where active = true
                order by kind
                """, (row, number) -> new DocumentModelResponse.Item(
                row.getString(1),
                row.getString(2),
                row.getTimestamp(3).toInstant()));
        return new DocumentModelResponse(models);
    }
}
