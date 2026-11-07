package cd.gouv.finances.signalements.report.taxpayer;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.repository.TaxpayerRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class VerifyTaxpayerCommand {

    private final TaxpayerRepository taxpayerRepository;
    private final TaxpayerVerification taxpayerVerification;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void execute(UUID id) {
        var taxpayer = taxpayerRepository.findById(id).orElseThrow(() -> new ReportRejected("taxpayer-absent", 404));
        if (taxpayer.isVerified()) {
            return;
        }
        if (!taxpayerVerification.confirmed(taxpayer.getTaxId())) {
            return;
        }
        taxpayer.verify(clock.instant());
    }
}
