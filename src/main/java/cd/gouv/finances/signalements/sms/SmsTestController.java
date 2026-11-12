package cd.gouv.finances.signalements.sms;

import cd.gouv.finances.signalements.report.ReportRejected;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SmsTestController {
    private final UnikronSmsGateway gateway;
    private final UnikronProperties properties;

    @PostMapping("/api/sms/tests")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public SmsResult test(@Valid @RequestBody(required = false) TestRequest request) {
        if (properties.mode() != UnikronProperties.Mode.DEVELOPMENT) throw new ReportRejected("sms-test-disabled", 409);
        var phone = request == null || request.phone() == null ? UnikronProperties.TEST_PHONE : request.phone();
        return gateway.send(phone, "Test SMS - plateforme de signalements du Ministere des Finances.");
    }

    public record TestRequest(@Size(max = 40) String phone) { }
}
