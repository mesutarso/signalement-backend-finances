package cd.gouv.finances.signalements.report.duplicate;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FingerprintBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FingerprintBackfill.class);

    private final StampPhotoFingerprints stampPhotoFingerprints;

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("photo-fingerprint").start(() -> {
            var stamped = 0;
            try {
                int batch;
                do {
                    batch = stampPhotoFingerprints.stampBatch();
                    stamped += batch;
                } while (batch > 0);
                log.info("Photo fingerprints ready: {}", stamped);
            } catch (RuntimeException exception) {
                log.warn("Photo fingerprints stopped after {}", stamped);
            }
        });
    }
}
