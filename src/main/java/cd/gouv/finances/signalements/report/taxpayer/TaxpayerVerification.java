package cd.gouv.finances.signalements.report.taxpayer;

import cd.gouv.finances.signalements.report.ReportRejected;
import org.springframework.stereotype.Component;

@Component
public class TaxpayerVerification {

    public boolean confirmed(String taxId) {
        throw new ReportRejected("taxpayer-unavailable", 503);
    }
}
