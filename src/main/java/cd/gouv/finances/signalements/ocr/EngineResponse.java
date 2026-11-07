package cd.gouv.finances.signalements.ocr;

import java.math.BigDecimal;
import java.util.List;

public record EngineResponse(
        String model,
        List<ExtractedField> fields,
        List<ExtractedArticle> articles,
        BigDecimal confidence) {

    public EngineResponse(String model, List<ExtractedField> fields) {
        this(model, fields, List.of(), null);
    }
}
