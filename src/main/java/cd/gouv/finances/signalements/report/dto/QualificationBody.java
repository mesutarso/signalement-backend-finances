package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QualificationBody(
        @NotBlank(message = "La décision n'est pas acceptée.")
        @Size(max = 16, message = "La décision n'est pas acceptée.")
        String decision,
        @Size(max = 32, message = "Ce constat n'est pas accepté.")
        String finding,
        @Size(max = 16, message = "Le niveau de preuve n'est pas accepté.")
        String proof,
        @NotBlank(message = "Indiquez le motif de la décision.")
        @Size(max = 500, message = "Le motif est trop long.")
        String justification,
        @Size(max = 160, message = "Le vendeur corrigé est trop long.")
        String sellerName,
        @Size(max = 40, message = "Le NIF corrigé est trop long.")
        String sellerTaxId,
        @Size(max = 10, message = "La date corrigée n'est pas acceptée.")
        String issuedOn,
        @Size(max = 40, message = "Le montant corrigé est trop long.")
        String amount,
        @Size(max = 80, message = "Le numéro corrigé est trop long.")
        String documentNumber,
        @Size(max = 80, message = "Le code corrigé est trop long.")
        String code,
        @Size(max = 500, message = "Les éléments manquants sont trop longs.")
        String missingElements,
        @Size(max = 80, message = "La référence de la DGI est trop longue.")
        String dgiReference) {
}
