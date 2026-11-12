package cd.gouv.finances.signalements.report.validation;

/** DGI-REP will implement this contract once its response schema and access are available. */
public interface FiscalRegistry {
    record Verification(String status, String evidenceReference) { }
    Verification lookup(String taxId, String sellerName);
}
