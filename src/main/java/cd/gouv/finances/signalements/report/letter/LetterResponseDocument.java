package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class LetterResponseDocument {

    static final int MAX_SIZE = 8_000_000;

    private LetterResponseDocument() {
    }

    public record Accepted(String contentType, String fingerprint) {
    }

    public static Accepted accept(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ReportRejected("response-document", 400);
        }
        if (content.length > MAX_SIZE) {
            throw new ReportRejected("response-size", 400);
        }
        return new Accepted(type(content), fingerprint(content));
    }

    private static String type(byte[] content) {
        if (starts(content, 0x25, 0x50, 0x44, 0x46)) {
            return "application/pdf";
        }
        if (starts(content, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (starts(content, 0x89, 0x50, 0x4E, 0x47)) {
            return "image/png";
        }
        if (content.length >= 12 && starts(content, 0x52, 0x49, 0x46, 0x46) && content[8] == 0x57 && content[9] == 0x45 && content[10] == 0x42 && content[11] == 0x50) {
            return "image/webp";
        }
        throw new ReportRejected("response-type", 400);
    }

    private static boolean starts(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (var index = 0; index < signature.length; index++) {
            if ((content[index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private static String fingerprint(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
