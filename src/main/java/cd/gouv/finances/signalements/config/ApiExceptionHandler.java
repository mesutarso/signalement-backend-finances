package cd.gouv.finances.signalements.config;

import cd.gouv.finances.signalements.access.AccessRejected;
import cd.gouv.finances.signalements.report.ReportRejected;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
            Map.entry("priority", "La priorité n'est pas acceptée."));

    @ExceptionHandler(AccessRejected.class)
    ResponseEntity<ApiError> accessRejected(AccessRejected rejected) {
        return ResponseEntity.status(rejected.status()).body(error(rejected.code()));
    }

    @ExceptionHandler(ReportRejected.class)
    ResponseEntity<ApiError> reportRejected(ReportRejected rejected) {
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

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("forbidden"));
    }
}
