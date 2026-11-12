package cd.gouv.finances.signalements.sms;

public record SmsResult(String status, String code, Integer httpStatus) {
    public static SmsResult unavailable(String code) { return new SmsResult("unavailable", code, null); }
    public String storedStatus() {
        return switch (status) { case "accepted" -> "accepted"; case "unknown" -> "unknown"; default -> "failed"; };
    }
    public boolean providerFailure() { return httpStatus != null && (httpStatus == 429 || httpStatus >= 500); }
}
