package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.report.query.ReadAdministrationReports;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.Analysis;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.Company;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.Declarant;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.Page;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.Payment;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdministrationReportsController {
    private final ReadAdministrationReports query;

    @GetMapping("/api/declarants/overview")
    public Page<Declarant> declarants(@RequestParam(defaultValue = "0") int page) {
        return query.declarants(page);
    }

    @GetMapping("/api/taxpayers/overview")
    public Page<Company> companies(@RequestParam(defaultValue = "0") int page) {
        return query.companies(page);
    }

    @GetMapping("/api/seller-files/companies")
    public Page<Company> establishments(@RequestParam(defaultValue = "0") int page) {
        return query.establishments(page);
    }

    @GetMapping("/api/dossier-payments")
    public Page<Payment> payments(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(required = false) UUID reportId,
                                  @RequestParam(required = false) String status) {
        return query.payments(page, reportId, status);
    }

    @GetMapping("/api/report-analysis/overview")
    public Analysis analysis() {
        return query.analysis();
    }
}
