package cd.gouv.finances.signalements.araka;

import java.net.URI;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.payments.araka")
public record ArakaProperties(URI baseUrl, String email, String password, Mode mode, boolean liveEnabled) {
    public enum Mode { SANDBOX, PRODUCTION }
    public static final String SANDBOX_HOST = "araka-api-uat.azurewebsites.net";
    private static final Map<String, String> PREFIXES = Map.of("MPESA", "81", "ORANGE", "84", "AIRTEL", "97", "AFRIMONEY", "90");

    public ArakaProperties {
        mode = mode == null ? Mode.SANDBOX : mode;
    }

    public boolean sandbox() { return mode == Mode.SANDBOX; }

    public boolean configured() {
        if (baseUrl == null || email == null || email.isBlank() || password == null || password.isBlank()) return false;
        if (baseUrl.getHost() == null || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null || baseUrl.getFragment() != null) return false;
        var local = "http".equals(baseUrl.getScheme()) && "127.0.0.1".equals(baseUrl.getHost());
        if (!"https".equals(baseUrl.getScheme()) && !local) return false;
        if (sandbox()) return SANDBOX_HOST.equals(baseUrl.getHost()) || local;
        return liveEnabled && "https".equals(baseUrl.getScheme()) && !SANDBOX_HOST.equals(baseUrl.getHost());
    }

    public static String wallet(String value) {
        var digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.matches("0[0-9]{9}")) digits = "243" + digits.substring(1);
        if (!digits.matches("243[0-9]{9}")) return null;
        return "+" + digits;
    }

    public static String provider(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "airtel" -> "AIRTEL";
            case "m-pesa", "mpesa" -> "MPESA";
            case "orange" -> "ORANGE";
            case "afrimoney" -> "AFRIMONEY";
            default -> null;
        };
    }

    public boolean permits(String wallet, String provider) {
        if (wallet == null || provider == null || !PREFIXES.containsKey(provider)) return false;
        if (!sandbox()) return configured();
        var prefix = "+243" + PREFIXES.get(provider) + "000000";
        return wallet.matches(java.util.regex.Pattern.quote(prefix) + "[1-4]");
    }

    @Override public String toString() { return "ArakaProperties[mode=" + mode + ", credentials=redacted]"; }
}
