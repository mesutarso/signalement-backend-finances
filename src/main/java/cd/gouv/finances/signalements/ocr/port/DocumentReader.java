package cd.gouv.finances.signalements.ocr.port;

import cd.gouv.finances.signalements.ocr.EngineResponse;

public interface DocumentReader {

    EngineResponse read(byte[] content, String mediaType);

    default String cacheModel() {
        return null;
    }
}
