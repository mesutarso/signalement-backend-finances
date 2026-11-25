package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.report.export.ReadOperationalReports;
import cd.gouv.finances.signalements.report.export.ReadOperationalReports.Filters;
import cd.gouv.finances.signalements.report.export.ReadOperationalReports.Options;
import cd.gouv.finances.signalements.report.export.ReadOperationalReports.Row;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/operational-reports")
public class OperationalReportsController {
    private final ReadOperationalReports reports;

    @GetMapping
    public Page<Row> preview(@ModelAttribute Filters filters, @RequestParam(defaultValue = "0") int page) {
        return reports.preview(filters, page);
    }

    @GetMapping("/options")
    public Options options() {
        return reports.options();
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@ModelAttribute Filters filters) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rapport-signalements.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(reports.csv(filters));
    }
}
