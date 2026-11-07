package cd.gouv.finances.signalements.report.taxpayer;

import cd.gouv.finances.signalements.report.entity.Taxpayer;
import cd.gouv.finances.signalements.report.repository.TaxpayerRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class KeepTaxpayersCommand {

    private final JdbcTemplate jdbc;
    private final TaxpayerRepository taxpayerRepository;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public int execute() {
        var rows = jdbc.query("""
                select distinct on (lower(btrim(latest.seller_tax_id))) btrim(latest.seller_tax_id), nullif(btrim(latest.seller_name), '')
                from (
                    select distinct on (qualification.report_id) qualification.seller_tax_id, qualification.seller_name, qualification.decision
                    from app.qualification qualification
                    order by qualification.report_id, qualification.recorded_at desc, qualification.id desc
                ) latest
                where latest.decision = 'confirm' and nullif(btrim(latest.seller_tax_id), '') is not null
                order by lower(btrim(latest.seller_tax_id))
                """, (row, number) -> new String[] {row.getString(1), row.getString(2)});
        var created = 0;
        var now = clock.instant();
        for (var row : rows) {
            if (taxpayerRepository.findByTaxId(row[0]).isPresent()) {
                continue;
            }
            taxpayerRepository.save(new Taxpayer(UUID.randomUUID(), row[0], row[1], now));
            created++;
        }
        return created;
    }
}
