package cd.gouv.finances.signalements.ocr;

public class EngineUnavailable extends RuntimeException {

    private final String code;

    public EngineUnavailable(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
