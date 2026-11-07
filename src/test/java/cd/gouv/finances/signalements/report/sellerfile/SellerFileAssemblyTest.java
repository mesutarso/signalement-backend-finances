package cd.gouv.finances.signalements.report.sellerfile;

import static org.assertj.core.api.Assertions.assertThat;

import cd.gouv.finances.signalements.report.sellerfile.SellerFileAssembly.Source;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SellerFileAssemblyTest {

    @Test
    void theSameTaxIdentifierGroupsReportsWithoutMergingDifferentNames() {
        var earlier = source(" A123 ", "Kiosque Nord", Instant.parse("2026-11-01T10:00:00Z"));
        var later = source("a123", "Kiosque Sud", Instant.parse("2026-11-06T10:00:00Z"));
        var other = source("b9", "Atelier", Instant.parse("2026-11-03T10:00:00Z"));

        var files = SellerFileAssembly.identified(List.of(earlier, later, other, source("  ", "Seul", Instant.parse("2026-11-07T10:00:00Z"))));

        assertThat(files).hasSize(2);
        assertThat(files.getFirst().taxId()).isEqualTo("a123");
        assertThat(files.getFirst().namesDiffer()).isTrue();
        assertThat(files.getFirst().sellerName()).isNull();
        assertThat(files.getFirst().reports()).extracting(report -> report.reportId()).containsExactly(later.reportId(), earlier.reportId());
        assertThat(files.get(1).taxId()).isEqualTo("b9");
        assertThat(files.get(1).sellerName()).isEqualTo("Atelier");
    }

    @Test
    void aSellerWithoutATaxIdentifierStaysAlone() {
        var first = source(null, "Même enseigne", Instant.parse("2026-11-02T10:00:00Z"));
        var second = source(" ", "Même enseigne", Instant.parse("2026-11-04T10:00:00Z"));

        var files = SellerFileAssembly.unidentified(List.of(first, second));

        assertThat(files).hasSize(2);
        assertThat(files).allSatisfy(file -> {
            assertThat(file.taxId()).isNull();
            assertThat(file.reports()).hasSize(1);
        });
        assertThat(files.getFirst().reports().getFirst().reportId()).isEqualTo(second.reportId());
    }

    private static Source source(String taxId, String sellerName, Instant qualifiedAt) {
        return new Source(
                UUID.randomUUID(),
                "EXT-1",
                LocalDateTime.parse("2026-10-30T08:00:00"),
                "Facture",
                "confirm",
                "non-normalized",
                "register",
                sellerName,
                taxId,
                "Constat retenu.",
                qualifiedAt);
    }
}
