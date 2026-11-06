package cd.gouv.finances.signalements.access.dto;

import java.util.List;

public record AccountPageResponse(List<AccountResponse> accounts, int page, int size, long total) {
}
