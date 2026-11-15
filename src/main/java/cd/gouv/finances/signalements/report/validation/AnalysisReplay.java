package cd.gouv.finances.signalements.report.validation;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** A fixed cutoff makes a historical reread resumable without invalidating newer OCR attempts. */
@Component
public class AnalysisReplay {
    private final Instant before;

    public AnalysisReplay(@Value("${app.validation.replay-before:}") String before) {
        this.before = before == null || before.isBlank() ? null : Instant.parse(before);
    }

    public Instant before() { return before; }

    public boolean reusable(AssessmentEvidence.Reading reading) {
        return reading != null && "READ".equals(reading.status())
                && (before == null || reading.createdAt() != null && !reading.createdAt().isBefore(before));
    }

    public boolean refresh() { return before != null; }
}
