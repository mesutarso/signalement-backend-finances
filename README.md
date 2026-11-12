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

## Circuit interne et recette

La [validation par les cinq critères et les récompenses configurables](VALIDATION.md) décrit le circuit actuel, la vérification fiscale manuelle en attendant DGI-REP et la reprise des anciennes décisions.

Le contrôle des bénéficiaires, les courriers par service, le budget et les archives de rapports sont décrits dans [la recette du 10 novembre 2026](RECETTE_2026-11-10.md). Elle fournit les commandes reproductibles et les limites de validation externe. Une identité vérifiée est requise avant toute approbation de prime. Les nouvelles API SITAX, les notifications et la gateway ne sont pas activées par ces corrections.

## SMS Unikron

Configurer `UNIKRON_ENDPOINT`, `UNIKRON_USER_NAME` et `UNIKRON_APP_KEY` dans `.env`. `UNIKRON_SENDER_ID` vaut `Jabari` par défaut. Le mode par défaut `UNIKRON_MODE=development` bloque tous les destinataires sauf `0827243119` et `0852214059`, convertis respectivement en `243827243119` et `243852214059`.

Les messages sont envoyés explicitement depuis `/messages`, par un superviseur ou administrateur. Le SMS de test est réservé aux administrateurs. Aucun brouillon existant n’est envoyé automatiquement. Les secrets, le contenu des SMS et les réponses brutes du fournisseur ne sont pas journalisés.

Le client réutilise sa connexion HTTP, avec un délai de connexion de 3 secondes, une requête limitée à 10 secondes et quatre appels simultanés maximum. Le circuit breaker utilise 10 appels, un minimum de 5 appels, un seuil de 50 % d’échecs, une ouverture de 60 secondes et une seule sonde de reprise. Les erreurs réseau, HTTP 429 et HTTP 5xx alimentent le circuit ; les erreurs locales et les autres refus HTTP ne le déclenchent pas. Aucune relance automatique n’est effectuée.

Un message transmis ou de résultat incertain ne peut pas être renvoyé par un double clic. Après une interruption, un envoi sans résultat reste à confirmer. La migration V45 conserve les brouillons et ajoute uniquement leur état courant d’envoi. Un HTTP 2xx signifie que le fournisseur a accepté la requête ; la réception sur le téléphone doit être confirmée séparément.

[La documentation Unikron](https://unikron.tech/doc/v0.2) impose un numéro international sans `+` ni `00`, un message de 160 caractères maximum et un expéditeur de 11 caractères maximum. Le client envoie les champs JSON `username`, `app_key`, `tel`, `message` et `from`, comme dans l’intégration PHP fournie.

Vérifications : `./mvnw clean verify`, puis `python scripts/recette_sms.py --runtime <configuration privée de recette>` contre un simulateur local. Le test réel `UnikronLiveTest` reste ignoré sauf si `UNIKRON_LIVE_TEST` vaut explicitement l’un des deux numéros autorisés et si les variables Unikron sont présentes dans l’environnement du processus ; il effectue alors un seul appel réel. Ne jamais activer cette variable dans la CI.
