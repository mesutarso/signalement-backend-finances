package cd.gouv.finances.signalements.access;

public class AccessRejected extends RuntimeException {

    private final String code;
    private final int status;

    public AccessRejected(String code, int status) {
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
