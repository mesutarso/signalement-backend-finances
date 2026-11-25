package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.Analysis;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.Company;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.CompanyDetails;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.Declarant;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.Page;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.Payment;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.UpdateRevision;
import cd.gouv.finances.signalements.report.validation.AnalysisJobs;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdministrationReportsController {
    private final ReadAdministrationReports query;
    private final AnalysisJobs jobs;

    public record RetryResult(int queued) { }

    @GetMapping("/api/report-updates")
    public UpdateRevision revision() {
        return query.revision();
    }

    @GetMapping("/api/declarants/overview")
    public Page<Declarant> declarants(@RequestParam(defaultValue = "0") int page) {
        return query.declarants(page);
    }

    @GetMapping("/api/taxpayers/overview")
    public Page<Company> companies(@RequestParam(defaultValue = "0") int page) {
        return query.companies(page);
    }

    @GetMapping("/api/seller-files/companies")
    public Page<Company> establishments(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(name = "q", required = false) String search) {
        return query.establishments(page, search);
    }

    @GetMapping("/api/seller-files/companies/details")
    public CompanyDetails companyDetails(@RequestParam(required = false) String nif,
                                         @RequestParam(required = false) String name,
                                         @RequestParam(defaultValue = "0") int page) {
        return query.companyDetails(nif, name, page);
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

    @PostMapping("/api/report-analysis/retry")
    public RetryResult retryAnalysis() {
        if (!query.analysis().enabled()) throw new ReportRejected("analysis-paused", 409);
        return new RetryResult(jobs.retryFailed());
    }
}
