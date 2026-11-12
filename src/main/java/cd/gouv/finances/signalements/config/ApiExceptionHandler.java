package cd.gouv.finances.signalements.config;

import cd.gouv.finances.signalements.access.AccessRejected;
import cd.gouv.finances.signalements.ocr.DocumentRejected;
import cd.gouv.finances.signalements.report.ReportRejected;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("rejected-credentials", "Identifiants refusés."),
            Map.entry("forbidden", "Cette action n'est pas autorisée."),
            Map.entry("validation", "Les informations saisies ne sont pas acceptées."),
            Map.entry("conflict", "Cet identifiant existe déjà."),
            Map.entry("last-administrator", "Le dernier administrateur doit rester actif."),
            Map.entry("absent", "Ce compte est introuvable."),
            Map.entry("report-absent", "Ce signalement est introuvable."),
            Map.entry("photo-absent", "Cette photo est introuvable."),
            Map.entry("period", "La date de fin précède la date de début."),
            Map.entry("source", "Les signalements ne peuvent pas être récupérés pour le moment."),
            Map.entry("import-running", "Une récupération est déjà en cours."),
            Map.entry("import-error", "Les signalements ne peuvent pas être récupérés pour le moment."),
            Map.entry("dispatch-error", "Les signalements ne peuvent pas être récupérés pour le moment."),
            Map.entry("page-limit", "La récupération s'est arrêtée avant la fin de la période."),
            Map.entry("missing-photo", "Une photo n'a pas pu être récupérée."),
            Map.entry("stale", "Ce signalement a été modifié. Actualisez avant de l'affecter."),
            Map.entry("assignee", "Seul un agent actif peut recevoir un signalement."),
            Map.entry("priority", "La priorité n'est pas acceptée."),
            Map.entry("empty", "Cette pièce est vide."),
            Map.entry("too-large", "Cette pièce est trop volumineuse."),
            Map.entry("reading-in-progress", "Une lecture est déjà en cours pour cette pièce. Actualisez dans un instant."),
            Map.entry("rejected-type", "Ce type de pièce ne peut pas être lu."),
            Map.entry("missing-reference", "Cette pièce est introuvable."),
            Map.entry("request-open", "Une demande est déjà ouverte pour cette pièce. Actualisez."),
            Map.entry("request-absent", "Cette demande est introuvable."),
            Map.entry("same-report", "La nouvelle pièce doit venir d'un autre signalement."),
            Map.entry("already-linked", "Cette demande est déjà rattachée à un autre signalement."),
            Map.entry("ambiguous-report", "Plusieurs signalements portent cette référence."),
            Map.entry("duplicate-absent", "Ce rapprochement est introuvable."),
            Map.entry("duplicate-decided", "Ce rapprochement a déjà été tranché."),
            Map.entry("duplicate-decision", "La décision n'est pas acceptée."),
            Map.entry("qualification-decision", "La décision n'est pas acceptée."),
            Map.entry("qualification-finding", "Ce constat ne peut pas être retenu sur une preuve suffisante."),
            Map.entry("qualification-proof", "Ce constat exige une vérification manuelle. La photo seule ne suffit pas."),
            Map.entry("qualification-correction", "Une correction accompagne seulement un constat retenu."),
            Map.entry("qualification-date", "La date corrigée n'est pas acceptée."),
            Map.entry("qualification-missing", "Indiquez les éléments qui manquent pour la confirmation."),
            Map.entry("qualification-reference", "Indiquez la référence de la réponse de la DGI."),
            Map.entry("qualification-waiting", "Ce refus attend d'abord la confirmation de la DGI."),
            Map.entry("abuse-absent", "Cet indice est introuvable."),
            Map.entry("abuse-decision", "La décision n'est pas acceptée."),
            Map.entry("supervision-absent", "Ce dossier n'a pas encore de qualification."),
            Map.entry("supervision-result", "La décision n'est pas acceptée."),
            Map.entry("letter-empty", "Aucun constat retenu avec un numéro d'impôt ne peut entrer dans le courrier."),
            Map.entry("letter-closed", "Un courrier de cette semaine est déjà validé."),
            Map.entry("letter-absent", "Ce courrier est introuvable."),
            Map.entry("letter-result", "La décision n'est pas acceptée."),
            Map.entry("letter-reason", "Indiquez le motif du renvoi."),
            Map.entry("letter-decided", "Ce courrier a déjà été tranché."),
            Map.entry("letter-annex", "Cette annexe n'est pas disponible en tableur."),
            Map.entry("response-letter", "La réponse se rattache à un courrier validé."),
            Map.entry("response-field", "Indiquez la date, le service, la référence et la conclusion."),
            Map.entry("response-length", "Le texte est trop long."),
            Map.entry("response-date", "La date de réponse n'est pas acceptée."),
            Map.entry("response-future", "La date de réponse ne peut pas être postérieure à aujourd'hui."),
            Map.entry("response-document", "Joignez la pièce de réponse."),
            Map.entry("response-size", "Cette pièce est trop volumineuse."),
            Map.entry("response-type", "Cette pièce n'est pas acceptée. Utilisez un PDF ou une image."),
            Map.entry("response-duplicate", "Cette référence est déjà enregistrée pour ce courrier."),
            Map.entry("response-absent", "Cette réponse est introuvable."),
            Map.entry("points-qualification", "Le constat doit être retenu avant le crédit."),
            Map.entry("points-finding", "Ce constat ne donne pas de points."),
            Map.entry("points-tax", "Le numéro d'impôt manque."),
            Map.entry("qualification-credited", "Ce constat a déjà des points. Enregistrez d'abord son infirmation dans Corrections pour annuler ses effets, puis sa nouvelle qualification."),
            Map.entry("reexamination-correction", "Modifiez d'abord la qualification du signalement. Un constat crédité doit passer par Corrections avant sa nouvelle décision."),
            Map.entry("campaign-budget", "Indiquez un budget positif et la référence de son approbation."),
            Map.entry("campaign-report", "Choisissez le rapport hebdomadaire ou mensuel."),
            Map.entry("points-period", "Le dépôt du signalement est hors de la période admissible de la campagne."),
            Map.entry("draw-identity", "Vérifiez l'identité des bénéficiaires des tickets avant le tirage pour garantir un seul lot par personne."),
            Map.entry("letter-routing", "Renseignez le service destinataire de chaque NIF avant de préparer les courriers."),
            Map.entry("letter-dispatch", "Validez le courrier puis renseignez sa date d'envoi et la référence du bordereau."),
            Map.entry("beneficiary-evidence", "Renseignez le dossier du bénéficiaire et le justificatif contrôlé."),
            Map.entry("beneficiary-locked", "Ce compte est déjà rattaché à un bénéficiaire vérifié. Son identité ne peut pas être remplacée."),
            Map.entry("beneficiary-unverified", "Vérifiez l'identité du bénéficiaire dans Situation du déclarant avant d'approuver sa prime."),
            Map.entry("beneficiary-ceiling", "Cette récompense dépasserait le plafond du bénéficiaire, tous comptes et versions confondus."),
            Map.entry("points-declarant", "Le déclarant n'est pas identifiable."),
            Map.entry("points-duplicate", "Ce signalement n'a pas la priorité sur l'opération."),
            Map.entry("points-day", "Cinq signalements de ce déclarant ont déjà été déposés ce jour-là."),
            Map.entry("points-spacing", "Cet établissement a déjà reçu des points depuis moins de trois jours."),
            Map.entry("points-reference", "Indiquez la référence de la confirmation."),
            Map.entry("points-open", "Ces points sont déjà crédités."),
            Map.entry("points-absent", "Aucun point à annuler."),
            Map.entry("points-reason", "Indiquez le motif de l'annulation."),
            Map.entry("prime-result", "La décision n'est pas acceptée."),
            Map.entry("prime-reason", "Indiquez le motif du renvoi."),
            Map.entry("prime-absent", "Cette récompense est introuvable."),
            Map.entry("prime-state", "Cette récompense n'est plus à revoir."),
            Map.entry("prime-finding", "Le constat retenu est introuvable."),
            Map.entry("prime-separation", "L'agent qui a retenu le constat ne peut pas autoriser la récompense."),
            Map.entry("prime-phone", "Le numéro de paiement doit être celui du déclarant, et un seul."),
            Map.entry("payment-empty", "Aucune prime approuvée n'est prête pour un état."),
            Map.entry("payment-open", "Un état de paiement est déjà en cours."),
            Map.entry("payment-absent", "Cet état est introuvable."),
            Map.entry("payment-state", "Cet état n'attend pas cette décision."),
            Map.entry("payment-result", "La décision n'est pas acceptée."),
            Map.entry("payment-reason", "Indiquez le motif."),
            Map.entry("payment-separation", "Cette étape doit être faite par un autre bureau."),
            Map.entry("payment-outcome", "Le résultat n'est pas accepté."),
            Map.entry("payment-reference", "Indiquez la référence de la transaction."),
            Map.entry("payment-line", "Cette récompense n'est pas dans cet état."),
            Map.entry("payment-paid", "Cette récompense est déjà payée."),
            Map.entry("payment-uncertain", "Le résultat incertain doit être confirmé avant une nouvelle tentative."),
            Map.entry("payment-operator", "Choisissez un moyen de paiement."),
            Map.entry("taxpayer-absent", "Ce contribuable est introuvable."),
            Map.entry("taxpayer-unavailable", "La vérification du numéro d'impôt est indisponible."),
            Map.entry("payment-sample-empty", "Aucune prime payée sur cette période."),
            Map.entry("payment-sample-period", "Indiquez une période valide."),
            Map.entry("payment-sample-open", "Cet échantillon existe déjà."),
            Map.entry("payment-sample-absent", "Cet échantillon est introuvable."),
            Map.entry("payment-sample-result", "Le résultat du contrôle n'est pas accepté."),
            Map.entry("payment-sample-reason", "Indiquez le motif de l'anomalie."),
            Map.entry("draw-empty", "Aucun signalement confirmé pour cette période."),
            Map.entry("draw-tickets", "Aucun ticket pour ce tirage."),
            Map.entry("draw-month", "Indiquez le mois."),
            Map.entry("draw-kind", "Le tirage n'est pas accepté."),
            Map.entry("draw-witness", "Indiquez le témoin."),
            Map.entry("draw-lots", "Indiquez au moins un lot."),
            Map.entry("draw-open", "Ce tirage est déjà organisé."),
            Map.entry("draw-absent", "Ce tirage est introuvable."),
            Map.entry("draw-state", "Ce tirage n'est pas publiable."),
            Map.entry("indicator-period", "Indiquez une période valide."),
            Map.entry("indicator-finding", "Ce constat n'est pas accepté."),
            Map.entry("correction-reason", "Indiquez le motif de la correction."),
            Map.entry("correction-state", "La confirmation n'est pas le dernier constat."),
            Map.entry("correction-open", "Cette confirmation est déjà tombée."),
            Map.entry("reexamination-text", "Indiquez la demande de réexamen."),
            Map.entry("reexamination-date", "La date de réception n'est pas acceptée."),
            Map.entry("reexamination-absent", "Cette demande est introuvable."),
            Map.entry("reexamination-decided", "Cette demande est déjà traitée."),
            Map.entry("reexamination-result", "La décision n'est pas acceptée."),
            Map.entry("reexamination-comment", "Le commentaire est trop long."),
            Map.entry("message-kind", "Le message n'est pas accepté."),
            Map.entry("message-open", "Ce message est déjà préparé."),
            Map.entry("suspension-action", "L'action n'est pas acceptée."),
            Map.entry("suspension-reason", "Indiquez le motif."),
            Map.entry("suspension-declarant", "Ce signalement n'a pas de déclarant identifiable."),
            Map.entry("suspension-state", "Le droit aux primes est déjà dans cet état."),
            Map.entry("prime-suspended", "Le droit aux primes est suspendu."),
            Map.entry("prime-lost", "Cette récompense est perdue : 90 jours se sont écoulés depuis le crédit sans numéro valide."),
            Map.entry("model-body", "Le modèle contient la référence une seule fois."),
            Map.entry("handover-note", "La note est trop longue."),
            Map.entry("handover-absent", "Ce lot est introuvable."),
            Map.entry("handover-open", "Ce lot est déjà remis."),
            Map.entry("rule-code", "Le code de la version n'est pas accepté."),
            Map.entry("rule-scale", "Le barème n'est pas accepté."),
            Map.entry("rule-bonus", "Le bonus est 0 ou 1."),
            Map.entry("rule-limit", "Les limites ne sont pas acceptées."),
            Map.entry("rule-tier", "Les paliers ne sont pas acceptés."),
            Map.entry("rule-open", "Cette version existe déjà."),
            Map.entry("rule-absent", "Cette version est introuvable."),
            Map.entry("rule-state", "Cette version est déjà active."),
            Map.entry("rule-finding", "Ce constat n'est pas accepté."),
            Map.entry("rule-sample", "L'exemple n'est pas accepté."));

    @ExceptionHandler(AccessRejected.class)
    ResponseEntity<ApiError> accessRejected(AccessRejected rejected) {
        return ResponseEntity.status(rejected.status()).body(error(rejected.code()));
    }

    @ExceptionHandler(ReportRejected.class)
    ResponseEntity<ApiError> reportRejected(ReportRejected rejected) {
        var workflowMessage = switch (rejected.code()) {
            case "assessment-absent" -> "Vérification introuvable.";
            case "sms-state" -> "Ce message est déjà transmis, en cours d’envoi ou à confirmer.";
            case "sms-absent" -> "Message introuvable.";
            case "sms-test-disabled" -> "Les essais SMS sont désactivés.";
            case "assessment-photo" -> "Choisissez une pièce jointe de ce signalement.";
            case "assessment-value" -> "Une vérification comporte une valeur non acceptée.";
            case "assessment-evidence" -> "Indiquez la source ou le justificatif de chaque vérification renseignée.";
            case "assessment-busy" -> "L’analyse des pièces est en cours. Actualisez le dossier avant de vérifier.";
            case "assessment-required" -> "Utilisez la nouvelle grille de vérification pour ce signalement.";
            case "reward-amount" -> "Indiquez un montant de récompense positif.";
            case "reward-validation" -> "Le signalement doit être validé avant de poursuivre le paiement.";
            case "points-retired" -> "L’attribution de points est remplacée par une récompense par signalement validé.";
            default -> null;
        };
        if (workflowMessage != null) return ResponseEntity.status(rejected.status()).body(new ApiError(rejected.code(), workflowMessage));
        if ("import-running".equals(rejected.code())) return ResponseEntity.status(409).body(new ApiError("import-running", "Un import est déjà en cours. Attendez sa fin."));
        if ("dispatch-error".equals(rejected.code())) return ResponseEntity.status(503).body(new ApiError("dispatch-error", "L’import ne peut pas démarrer. Réessayez."));
        return ResponseEntity.status(rejected.status()).body(error(rejected.code()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException exception) {
        var fieldError = exception.getBindingResult().getFieldError();
        var message = fieldError == null || fieldError.getDefaultMessage() == null
                ? MESSAGES.get("validation")
                : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(new ApiError("validation", message));
    }

    private static ApiError error(String code) {
        return new ApiError(code, MESSAGES.getOrDefault(code, MESSAGES.get("validation")));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> stale() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("stale"));
    }

    @ExceptionHandler(DocumentRejected.class)
    ResponseEntity<ApiError> documentRejected(DocumentRejected rejected) {
        return ResponseEntity.status("reading-in-progress".equals(rejected.code())
                ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST).body(error(rejected.code()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge() {
        return ResponseEntity.badRequest().body(error("response-size"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("forbidden"));
    }
}
