package cd.gouv.finances.signalements.report;

public class ReportRejected extends RuntimeException {

    private final String code;
    private final int status;

    public ReportRejected(String code, int status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }
}
