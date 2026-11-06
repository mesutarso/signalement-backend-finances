package cd.gouv.finances.signalements.ocr;

public class DocumentRejected extends RuntimeException {

    private final String code;

    public DocumentRejected(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
