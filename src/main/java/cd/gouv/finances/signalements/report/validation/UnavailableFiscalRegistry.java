package cd.gouv.finances.signalements.report.validation;

import org.springframework.stereotype.Component;

@Component
public class UnavailableFiscalRegistry implements FiscalRegistry {
    @Override
    public Verification lookup(String taxId, String sellerName) {
        return new Verification("unavailable", null);
    }
}
