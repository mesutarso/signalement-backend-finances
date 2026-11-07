package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.report.dto.SellerFilePageResponse;
import cd.gouv.finances.signalements.report.sellerfile.ReadSellerFilesQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reports")
@SecurityRequirement(name = "auth")
public class SellerFileController {

    private final ReadSellerFilesQuery readSellerFilesQuery;

    @GetMapping("/api/seller-files")
    @Operation(summary = "Lists company files built from retained findings that share a tax identifier")
    public SellerFilePageResponse list(
            @RequestParam(defaultValue = "identified") String status,
            @RequestParam(defaultValue = "0") int page) {
        return readSellerFilesQuery.execute(status, page);
    }
}
