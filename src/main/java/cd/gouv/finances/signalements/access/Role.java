package cd.gouv.finances.signalements.access;

public enum Role {
    AGENT("Agent de traitement", "Examine et complète les signalements."),
    SUPERVISOR("Superviseur", "Coordination EPSMO-FACNO : supervision, courriers, paramètres et réexamens."),
    DGI("DGI", "Vérifie les contribuables et confirme les manquements."),
    DGI_OPERATIONS("Services opérationnels DGI", "Consulte les dossiers transmis et enregistre les conclusions."),
    CELLULE("Cellule fiscale", "Vise les états de paiement."),
    CABINET("Cabinet du Ministre", "Autorise le paiement des primes."),
    PAYMASTER("Gestionnaire des fonds", "Exécute les paiements Mobile Money."),
    AUDITOR("Auditeur", "Consulte les données autorisées sans les modifier."),
    ADMINISTRATOR("Administrateur", "Hérite de toutes les permissions et administre les utilisateurs.");

    private final String label;
    private final String description;
    Role(String label, String description) { this.label = label; this.description = description; }
    public String label() { return label; }
    public String description() { return description; }
}
