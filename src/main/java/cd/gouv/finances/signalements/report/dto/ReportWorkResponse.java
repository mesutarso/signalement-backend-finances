package cd.gouv.finances.signalements.report.dto;

import java.time.LocalDate;
public record ReportWorkResponse(String sellerName, String sellerTaxId, String documentNumber,
                                 LocalDate documentDate, String totalAmount, String currency,
                                 String vatStatus, String jobState, String reason, boolean photoRequested) { }
