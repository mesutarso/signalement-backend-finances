package cd.gouv.finances.signalements.report.prime;

import cd.gouv.finances.signalements.report.entity.PayoutNumber;
import cd.gouv.finances.signalements.report.repository.PayoutNumberRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordPayoutCommand {

    private final PrimeContext primeContext;
    private final PayoutNumberRepository payoutNumberRepository;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public Optional<UUID> execute(String declarantId, UUID actorId) {
        if (declarantId == null || declarantId.isBlank()) {
            return Optional.empty();
        }
        var declarant = declarantId.trim();
        primeContext.lockPaymentIdentity(declarant);
        var phone = primeContext.phone(declarant, null);
        var now = clock.instant();
        var existing = payoutNumberRepository.findByDeclarantId(declarant);
        if (existing.isEmpty()) {
            var created = new PayoutNumber(UUID.randomUUID(), declarant, phone.number(), phone.state(), actorId, now);
            payoutNumberRepository.save(created);
            return Optional.of(created.getId());
        }
        var current = existing.get();
        if (!current.record(phone.number(), phone.state(), actorId, now)) {
            return Optional.empty();
        }
        return Optional.of(current.getId());
    }
}
