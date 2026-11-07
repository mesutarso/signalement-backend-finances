package cd.gouv.finances.signalements.report.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cd.gouv.finances.signalements.report.ReportRejected;
import org.junit.jupiter.api.Test;

class PaymentSimulationTest {

    @Test
    void namesTheFourSimulatedChannels() {
        assertThat(PaymentSimulation.reference(PaymentSimulation.channel("airtel"), 1)).isEqualTo("SIM-AIRTEL-000001");
        assertThat(PaymentSimulation.reference(PaymentSimulation.channel("m-pesa"), 2)).isEqualTo("SIM-MPESA-000002");
        assertThat(PaymentSimulation.reference(PaymentSimulation.channel("orange"), 3)).isEqualTo("SIM-ORANGE-000003");
        assertThat(PaymentSimulation.reference(PaymentSimulation.channel("afrimoney"), 4)).isEqualTo("SIM-AFRIMONEY-000004");
    }

    @Test
    void rejectsAChannelOutsideTheSimulation() {
        assertThatThrownBy(() -> PaymentSimulation.channel("banque"))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("payment-operator");
    }
}
