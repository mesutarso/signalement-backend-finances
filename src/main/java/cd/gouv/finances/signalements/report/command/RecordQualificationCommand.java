package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Qualification;
import cd.gouv.finances.signalements.report.qualification.QualificationRules;
import cd.gouv.finances.signalements.report.repository.QualificationRepository;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordQualificationCommand {

    private final ReportRepository reportRepository;
    private final QualificationRepository qualificationRepository;
    private final cd.gouv.finances.signalements.report.points.PointsContext pointsContext;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void execute(UUID reportId, QualificationRequest request, UUID actorId) {
        pointsContext.lock();
        if (!reportRepository.existsById(reportId)) {
            throw new ReportRejected("report-absent", 404);
        }
        var seller = blank(request.sellerName());
        var taxId = blank(request.sellerTaxId());
        var amount = blank(request.amount());
        var number = blank(request.documentNumber());
        var code = blank(request.code());
        var issued = date(blank(request.issuedOn()));
        var corrected = seller != null || taxId != null || amount != null || number != null || code != null || issued != null;
        var latest = qualificationRepository.findByReportIdOrderByRecordedAtDesc(reportId);
        var waiting = !latest.isEmpty() && ("wait".equals(latest.getFirst().getDecision())
                || ("confirm".equals(latest.getFirst().getDecision()) && "refusal".equals(latest.getFirst().getFinding())
                    && java.util.Objects.equals(latest.getFirst().getDgiReference(), blank(request.dgiReference()))));
        var accepted = QualificationRules.accept(
                request.decision(),
                blank(request.finding()),
                blank(request.proof()),
                corrected,
                blank(request.missingElements()),
                blank(request.dgiReference()),
                waiting);
        var justification = request.justification().trim();
        // The seller must remain identifiable while a refusal goes to the DGI and back.
        if (waiting && "confirm".equals(accepted.decision()) && accepted.reference() != null) {
            var previous = latest.getFirst();
            seller = seller == null ? previous.getSellerName() : seller;
            taxId = taxId == null ? previous.getSellerTaxId() : taxId;
            issued = issued == null ? previous.getIssuedOn() : issued;
            amount = amount == null ? previous.getAmount() : amount;
            number = number == null ? previous.getDocumentNumber() : number;
            code = code == null ? previous.getCode() : code;
        }
        var keepsCorrection = !"reject".equals(accepted.decision());
        var sellerName = keepsCorrection ? seller : null;
        var sellerTaxId = keepsCorrection ? taxId : null;
        var issuedOn = keepsCorrection ? issued : null;
        var keptAmount = keepsCorrection ? amount : null;
        var documentNumber = keepsCorrection ? number : null;
        var readCode = keepsCorrection ? code : null;
        if (!latest.isEmpty() && latest.getFirst().same(
                accepted.decision(),
                accepted.finding(),
                accepted.proof(),
                justification,
                sellerName,
                sellerTaxId,
                issuedOn,
                keptAmount,
                documentNumber,
                readCode,
                accepted.missing(),
                accepted.reference())) {
            return;
        }
        if (qualificationRepository.hasCreditedPoints(reportId)) {
            throw new ReportRejected("qualification-credited", 409);
        }
        qualificationRepository.save(new Qualification(
                UUID.randomUUID(),
                reportId,
                accepted.decision(),
                accepted.finding(),
                accepted.proof(),
                justification,
                sellerName,
                sellerTaxId,
                issuedOn,
                keptAmount,
                documentNumber,
                readCode,
                accepted.missing(),
                accepted.reference(),
                actorId,
                clock.instant()));
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ReportRejected("qualification-date", 400);
        }
    }

    public record QualificationRequest(
            String decision,
            String finding,
            String proof,
            String justification,
            String sellerName,
            String sellerTaxId,
            String issuedOn,
            String amount,
            String documentNumber,
            String code,
            String missingElements,
            String dgiReference) {
    }
}
