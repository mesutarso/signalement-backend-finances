package cd.gouv.finances.signalements.access;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Central, versioned RBAC catalogue. Administrators inherit every permission, including future additions. */
public enum Permission {
    REPORT_READ("report.read", "Signalements", "Consulter les signalements", Role.AGENT, Role.SUPERVISOR, Role.AUDITOR, Role.DGI),
    REPORT_IDENTITY("report.identity", "Signalements", "Consulter l’identité des déclarants", Role.AGENT, Role.SUPERVISOR),
    REPORT_REVIEW("report.review", "Signalements", "Compléter et qualifier une facture", Role.AGENT, Role.SUPERVISOR, Role.DGI),
    REPORT_ANALYZE("report.analyze", "Signalements", "Déclencher une lecture OCR", Role.AGENT, Role.SUPERVISOR, Role.DGI),
    REPORT_ASSIGN("report.assign", "Signalements", "Affecter les signalements", Role.SUPERVISOR),
    IMPORT_READ("import.read", "Signalements", "Consulter les imports", Role.AGENT, Role.SUPERVISOR, Role.AUDITOR),
    IMPORT_RUN("import.run", "Signalements", "Déclencher et reprendre un import", Role.SUPERVISOR),
    TAXPAYER_READ("taxpayer.read", "Dossiers", "Consulter les contribuables", Role.AGENT, Role.SUPERVISOR, Role.AUDITOR, Role.DGI),
    TAXPAYER_VERIFY("taxpayer.verify", "Dossiers", "Vérifier et conserver les contribuables", Role.SUPERVISOR, Role.DGI),
    DOSSIER_READ("dossier.read", "Dossiers", "Consulter les dossiers des établissements", Role.AGENT, Role.SUPERVISOR, Role.AUDITOR, Role.DGI),
    DECLARANT_READ("declarant.read", "Dossiers", "Consulter la situation des déclarants", Role.AGENT, Role.SUPERVISOR, Role.AUDITOR),
    LETTER_READ("letter.read", "Courriers", "Consulter les courriers et leurs annexes", Role.SUPERVISOR, Role.AUDITOR, Role.DGI, Role.DGI_OPERATIONS),
    LETTER_PREPARE("letter.prepare", "Courriers", "Préparer les courriers", Role.SUPERVISOR),
    LETTER_VALIDATE("letter.validate", "Courriers", "Valider ou refuser un courrier", Role.SUPERVISOR),
    LETTER_DISPATCH("letter.dispatch", "Courriers", "Gérer le routage et les transmissions", Role.SUPERVISOR),
    LETTER_RESPONSE("letter.response", "Courriers", "Enregistrer les conclusions des services", Role.SUPERVISOR, Role.DGI, Role.DGI_OPERATIONS),
    MESSAGE_READ("message.read", "Messages", "Consulter les messages aux déclarants", Role.AGENT, Role.SUPERVISOR, Role.AUDITOR),
    MESSAGE_PREPARE("message.prepare", "Messages", "Préparer les messages", Role.SUPERVISOR),
    MESSAGE_SEND("message.send", "Messages", "Transmettre les messages", Role.SUPERVISOR),
    TEMPLATE_READ("template.read", "Paramétrage", "Consulter les modèles de documents", Role.AGENT, Role.SUPERVISOR, Role.AUDITOR),
    TEMPLATE_WRITE("template.write", "Paramétrage", "Modifier les modèles de documents", Role.SUPERVISOR),
    REWARD_READ("reward.read", "Récompenses", "Consulter les récompenses et numéros de paiement", Role.SUPERVISOR, Role.AUDITOR),
    REWARD_APPROVE("reward.approve", "Récompenses", "Approuver les récompenses et coordonnées", Role.SUPERVISOR),
    BENEFICIARY_VERIFY("beneficiary.verify", "Récompenses", "Vérifier les bénéficiaires", Role.SUPERVISOR),
    PAYMENT_READ("payment.read", "Paiements", "Consulter les états et les versements", Role.SUPERVISOR, Role.AUDITOR, Role.CELLULE, Role.CABINET, Role.PAYMASTER),
    PAYMENT_PREPARE("payment.prepare", "Paiements", "Préparer les états de paiement", Role.SUPERVISOR),
    PAYMENT_CONTROL("payment.control", "Paiements", "Contrôler un état de paiement", Role.SUPERVISOR),
    PAYMENT_VISA("payment.visa", "Paiements", "Viser un état de paiement", Role.CELLULE),
    PAYMENT_AUTHORIZE("payment.authorize", "Paiements", "Autoriser le paiement des primes", Role.CABINET),
    PAYMENT_EXECUTE("payment.execute", "Paiements", "Exécuter les paiements Mobile Money", Role.PAYMASTER),
    PAYMENT_CHECK("payment.check", "Paiements", "Rapprocher les retours de paiement", Role.PAYMASTER),
    PAYMENT_TEST("payment.test", "Paiements", "Exécuter les essais techniques de paiement"),
    SAMPLE_READ("sample.read", "Contrôle", "Consulter les échantillons de contrôle", Role.SUPERVISOR, Role.AUDITOR),
    SAMPLE_REVIEW("sample.review", "Contrôle", "Préparer et contrôler les échantillons", Role.SUPERVISOR),
    DRAW_READ("draw.read", "Tirages", "Consulter les tirages", Role.AGENT, Role.SUPERVISOR, Role.AUDITOR),
    DRAW_MANAGE("draw.manage", "Tirages", "Organiser publier et remettre les lots", Role.SUPERVISOR),
    REEXAM_READ("reexam.read", "Contrôle", "Consulter les demandes de réexamen", Role.AGENT, Role.SUPERVISOR, Role.AUDITOR),
    REEXAM_DECIDE("reexam.decide", "Contrôle", "Enregistrer et décider un réexamen", Role.SUPERVISOR),
    CORRECTION_READ("correction.read", "Contrôle", "Consulter les corrections", Role.AGENT, Role.SUPERVISOR, Role.AUDITOR),
    CORRECTION_DECIDE("correction.decide", "Contrôle", "Rectifier un constat confirmé", Role.SUPERVISOR),
    ABUSE_READ("abuse.read", "Contrôle", "Consulter les indices et suspensions", Role.SUPERVISOR, Role.AUDITOR),
    ABUSE_WRITE("abuse.write", "Contrôle", "Documenter les abus et suspendre les primes", Role.SUPERVISOR),
    SUPERVISION_READ("supervision.read", "Contrôle", "Consulter les revues de supervision", Role.SUPERVISOR, Role.AUDITOR),
    SUPERVISION_WRITE("supervision.write", "Contrôle", "Enregistrer un contrôle de supervision", Role.SUPERVISOR),
    INDICATOR_READ("indicator.read", "Rapports", "Consulter les indicateurs et rapports", Role.AGENT, Role.SUPERVISOR, Role.AUDITOR, Role.DGI),
    REPORTING_WRITE("reporting.write", "Rapports", "Générer les rapports de campagne", Role.SUPERVISOR),
    SETTING_READ("setting.read", "Paramétrage", "Consulter le budget et les règles", Role.SUPERVISOR, Role.AUDITOR),
    SETTING_WRITE("setting.write", "Paramétrage", "Modifier le budget et les règles", Role.SUPERVISOR),
    AUDIT_READ("audit.read", "Administration", "Consulter le journal d’audit", Role.SUPERVISOR, Role.AUDITOR),
    USER_READ("user.read", "Administration", "Consulter les utilisateurs", Role.AUDITOR),
    USER_CREATE("user.create", "Administration", "Créer les utilisateurs"),
    USER_UPDATE("user.update", "Administration", "Modifier les utilisateurs et leurs rôles"),
    USER_DELETE("user.delete", "Administration", "Supprimer les utilisateurs"),
    USER_RESET_PASSWORD("user.reset-password", "Administration", "Réinitialiser les mots de passe"),
    USER_UNLOCK("user.unlock", "Administration", "Déverrouiller les utilisateurs"),
    RBAC_READ("rbac.read", "Administration", "Consulter la matrice des rôles et permissions", Role.AUDITOR),
    SMS_TEST("sms.test", "Messages", "Exécuter les essais SMS");

    private final String code;
    private final String group;
    private final String label;
    private final Set<Role> roles;

    Permission(String code, String group, String label, Role... roles) {
        this.code = code; this.group = group; this.label = label; this.roles = Set.of(roles);
    }
    public String code() { return code; }
    public String group() { return group; }
    public String label() { return label; }
    public boolean grantedTo(Role role) { return role == Role.ADMINISTRATOR || roles.contains(role); }
    public static List<String> forRole(Role role) {
        return Arrays.stream(values()).filter(p -> p.grantedTo(role)).map(Permission::code).toList();
    }
}
