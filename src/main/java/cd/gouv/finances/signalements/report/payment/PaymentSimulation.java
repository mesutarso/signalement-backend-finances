package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.util.Set;

public final class PaymentSimulation {

    public static final String AIRTEL = "airtel";
    public static final String M_PESA = "m-pesa";
    public static final String ORANGE = "orange";
    public static final String AFRIMONEY = "afrimoney";

    private static final Set<String> CHANNELS = Set.of(AIRTEL, M_PESA, ORANGE, AFRIMONEY);

    private PaymentSimulation() {
    }

    public static String channel(String value) {
        var channel = value == null ? "" : value.trim();
        if (!CHANNELS.contains(channel)) {
            throw new ReportRejected("payment-operator", 400);
        }
        return channel;
    }

    public static String reference(String channel, long number) {
        var code = switch (channel) {
            case AIRTEL -> "AIRTEL";
            case M_PESA -> "MPESA";
            case ORANGE -> "ORANGE";
            case AFRIMONEY -> "AFRIMONEY";
            default -> throw new ReportRejected("payment-operator", 400);
        };
        return "SIM-" + code + "-" + "%06d".formatted(number);
    }
}
