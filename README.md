# Backend

Spring Boot, PostgreSQL et Redis. Tout se lance depuis ce dossier.

```bash
cp .env.example .env
docker compose up -d
set -a && source .env && set +a
./mvnw spring-boot:run
```

Les tests : `./mvnw test`.

La documentation de l'API est sur http://localhost:8080/scalar pendant que le serveur tourne.

La lecture des pièces utilise le service Flask de Herdys. Configurer son URL complète, le jeton partagé et le fournisseur selon [le guide OCR](OCR_HERDYS.md). Les clés fournisseurs résident dans Flask ; le frontend ne les reçoit pas.

## Imports FACNO

La page `/imports` propose le lancement par période, le suivi des compteurs et la relance d’une période déjà exécutée. Les traitements se font en arrière-plan ; un seul import peut être actif, y compris entre le cron et les lancements manuels. Les auditeurs ne peuvent pas lancer ni consulter le suivi des imports.

La planification est active par défaut à 00 h, 03 h, 06 h, 09 h, 12 h, 15 h, 18 h et 21 h, fuseau `Africa/Kinshasa`. Le backend doit rester en cours d’exécution. Elle reprend la dernière période terminée avec un jour de recouvrement ; sans historique compatible, elle récupère les trois derniers jours. Utiliser le formulaire pour une reprise historique plus ancienne.

Variables facultatives : `REPORTS_SCHEDULE_ENABLED=false` désactive le cron ; `REPORTS_IMPORT_CRON="0 0 */3 * * *"` définit son expression ; `REPORTS_IMPORT_ON_STARTUP=false` désactive le lancement au démarrage. La clé `FACNO_API_KEY` est nécessaire à la récupération.

Les traitements sans activité depuis dix minutes sont marqués interrompus par le contrôle exécuté chaque minute, puis peuvent être relancés. Les anciens imports, enregistrés avant la migration V7 sans période, restent consultables ; leur période doit être saisie dans le formulaire pour les rejouer.

Les appels FACNO (liste, détail et images) utilisent Resilience4j CircuitBreaker : fenêtre de 10 appels, au moins 5 appels, seuil d’échecs de 50 %, suspension de 60 secondes puis un appel de contrôle. Une ouverture apparaît comme un échec explicite dans le suivi ; la période peut être relancée après la suspension. Les contraintes uniques sur les identifiants source des signalements et sur les photos protègent les réimports contre les doublons.
