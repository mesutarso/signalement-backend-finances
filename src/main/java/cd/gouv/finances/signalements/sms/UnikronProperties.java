package cd.gouv.finances.signalements.sms;

import java.net.URI;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.sms.unikron")
public record UnikronProperties(String endpoint, String username, String appKey, String senderId, Mode mode) {
    public enum Mode { DEVELOPMENT, PRODUCTION }
    public static final String TEST_PHONE = "0827243119";
    public static final Set<String> TEST_RECIPIENTS = Set.of("243827243119", "243852214059");

    public UnikronProperties {
        senderId = senderId == null || senderId.isBlank() ? "FACNO RDC" : senderId.trim();
        mode = mode == null ? Mode.DEVELOPMENT : mode;
    }

    public boolean configured() {
        if (blank(username) || blank(appKey) || blank(endpoint) || senderId.length() > 11) return false;
        try {
            var uri = URI.create(endpoint);
            return uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException invalid) { return false; }
    }

    public static String cleanPhone(String phone) {
        var digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.matches("0[0-9]{9}")) digits = "243" + digits.substring(1);
        return digits;
    }

    public boolean permitted(String phone) {
        if (!phone.matches("[1-9][0-9]{7,14}")) return false;
        return mode == Mode.PRODUCTION || TEST_RECIPIENTS.contains(phone);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    @Override public String toString() { return "UnikronProperties[mode=" + mode + ", configured=" + configured() + "]"; }
}
