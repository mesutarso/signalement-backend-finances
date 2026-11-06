package cd.gouv.finances.signalements.ocr;

import java.util.List;

public record EngineResponse(String model, List<ExtractedField> fields) {
}
