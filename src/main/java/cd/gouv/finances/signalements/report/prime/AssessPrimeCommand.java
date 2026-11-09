package cd.gouv.finances.signalements.report.prime;

import cd.gouv.finances.signalements.report.entity.Prime;
import cd.gouv.finances.signalements.report.repository.PrimeRepository;
import cd.gouv.finances.signalements.report.rule.ActiveRule;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AssessPrimeCommand {

    private final PrimeContext primeContext;
    private final PrimeRepository primeRepository;
    private final RecordPayoutCommand recordPayoutCommand;
    private final PrimeGuard primeGuard;
    private final ActiveRule activeRule;
    private final PrimeLoss primeLoss;
    private final Clock clock;

    public record Change(UUID id, String action) {
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public List<Change> execute(String declarantId, UUID actorId) {
        return execute(declarantId, actorId, null);
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public List<Change> execute(String declarantId, UUID actorId, String ruleVersion) {
        if (declarantId == null || declarantId.isBlank()) {
            return List.of();
        }
        var declarant = declarantId.trim();
        var changes = new ArrayList<Change>();
        recordPayoutCommand.execute(declarant, actorId).ifPresent(id -> changes.add(new Change(id, "payout-record")));
        var now = clock.instant();
        if (primeLoss.capture(declarant, now) || primeGuard.suspended(declarant)) {
            withdrawUnpaid(primeRepository.findByDeclarantId(declarant), actorId, now, changes);
            return List.copyOf(changes);
        }
        var rule = ruleVersion == null || ruleVersion.isBlank() ? activeRule.requireActive() : activeRule.require(ruleVersion);
        var net = primeContext.netPoints(declarant, rule.code());
        var phone = primeContext.phone(declarant, rule.code());
        var existing = primeRepository.findByDeclarantIdAndRuleVersion(declarant, rule.code());
        if (!PrimeContext.READY.equals(phone.state())) {
            withdrawUnpaid(existing, actorId, now, changes);
            return List.copyOf(changes);
        }
        var reached = PrimeScale.reached(net, rule.ceiling(), rule.tiers());
        for (var tier : reached) {
            var current = existing.stream().filter(prime -> prime.getThreshold() == tier.threshold()).findFirst();
            if (current.isEmpty()) {
                var created = new Prime(
                        UUID.randomUUID(),
                        declarant,
                        phone.number(),
                        rule.code(),
                        tier.threshold(),
                        tier.amount(),
                        actorId,
                        now);
                primeRepository.save(created);
                changes.add(new Change(created.getId(), "prime-proposal"));
                continue;
            }
            var prime = current.get();
            if (Prime.RETURNED.equals(prime.getStatus())) {
                continue;
            }
            if ((Prime.PROPOSED.equals(prime.getStatus()) || Prime.APPROVED.equals(prime.getStatus())) && phone.number().equals(prime.getPhone())) {
                continue;
            }
            if (Prime.WITHDRAWN.equals(prime.getStatus()) || !phone.number().equals(prime.getPhone())) {
                prime.propose(phone.number(), actorId, now);
                changes.add(new Change(prime.getId(), "prime-proposal"));
            }
        }
        for (var prime : existing) {
            var stillReached = reached.stream().anyMatch(tier -> tier.threshold() == prime.getThreshold());
            if (!stillReached && !Prime.WITHDRAWN.equals(prime.getStatus())) {
                prime.withdraw(actorId, now);
                changes.add(new Change(prime.getId(), "prime-withdrawal"));
            }
        }
        return List.copyOf(changes);
    }

    private void withdrawUnpaid(List<Prime> existing, UUID actorId, java.time.Instant now, List<Change> changes) {
        for (var prime : existing) {
            if (!Prime.WITHDRAWN.equals(prime.getStatus()) && !primeGuard.paid(prime.getId())) {
                prime.withdraw(actorId, now);
                changes.add(new Change(prime.getId(), "prime-withdrawal"));
            }
        }
    }
}
