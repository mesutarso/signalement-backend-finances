package cd.gouv.finances.signalements.araka;

public record ArakaResult(String state, String code, String transactionId, boolean providerFailure) {
    public static ArakaResult unknown(String code) { return new ArakaResult("unknown", code, null, false); }
    public static ArakaResult failed(String code) { return new ArakaResult("failed", code, null, false); }
}
