package cd.gouv.finances.signalements.ocr.herdys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.ocr.DocumentContract;
import cd.gouv.finances.signalements.ocr.command.RequestReadingCommand;
import cd.gouv.finances.signalements.ocr.command.StoreReading;
import cd.gouv.finances.signalements.ocr.port.DocumentReader;
import cd.gouv.finances.signalements.ocr.repository.ReadingRepository;
import cd.gouv.finances.signalements.report.mapper.PhotoReadingMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HerdysReadingFlowTest {

    @Test
    void preservesFiscalFieldsThroughStorageAndThePhotoResponse() throws Exception {
        var repository = mock(ReadingRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var body = new ObjectMapper().readTree(getClass().getResourceAsStream("/ocr/herdys-response.json"));
        DocumentReader reader = (content, type) -> HerdysResponse.read(body, "openai");
        var command = new RequestReadingCommand(reader, new StoreReading(repository));

        var photo = PhotoReadingMapper.from(command.execute("photo-1", new byte[] {1}, "image/png", true));

        assertThat(photo.status()).isEqualTo("READ");
        assertThat(photo.currency()).isEqualTo("USD");
        assertThat(photo.amount()).isEqualTo("116.00");
        assertThat(photo.netAmount()).isEqualTo("100.00");
        assertThat(photo.vatAmount()).isEqualTo("16.00");
        assertThat(photo.vatRate()).isEqualTo("16");
        assertThat(photo.paidAmount()).isEqualTo("120.00");
        assertThat(photo.code()).isEqualTo("VERIF-001");
        assertThat(photo.defCode()).isEqualTo("DEF-CODE");
        assertThat(photo.defNumber()).isEqualTo("0000123");
        assertThat(photo.model()).isEqualTo("herdys-openai");
        assertThat(photo.schemaVersion()).isEqualTo(DocumentContract.VERSION);
        assertThat(photo.articles()).hasSize(1);
    }
}
