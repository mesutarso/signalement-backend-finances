# Backend et données — Coolify

Le raccordement à l’application OCR privée est décrit dans [OCR_HERDYS.md](OCR_HERDYS.md). Les variables `HERDYS_OCR_API_TOKEN`, `HERDYS_OCR_MODEL` et `HERDYS_OCR_TIMEOUT` complètent l’URL déjà prévue. Leur présence dans le code ne signifie pas que le service est configuré en production.

Source : `https://github.com/mesutarso/signalement-backend-finances.git`, branche `main`.

Créer une application depuis ce dépôt avec le build Docker Compose. Répertoire `/`, fichier `/docker-compose.production.yml`. La stack contient backend, PostgreSQL 18 et Redis 8. Ne configurer aucun domaine ni port public pour cette stack.

Le réseau externe `coolify` doit exister sur le serveur et être partagé avec l’application frontend. L’alias privé du backend est `signalements-backend` ; le frontend utilise `BACKEND_URL=http://signalements-backend:8080`. PostgreSQL et Redis restent sur le réseau interne `data`, inaccessible au frontend. Le backend conserve une sortie Internet pour FACNO et la lecture des pièces.

## Variables

Saisir les valeurs du fichier `.env.production.example` dans Coolify. Coolify génère les mots de passe PostgreSQL et Redis à partir des variables SERVICE_PASSWORD_64_POSTGRES et SERVICE_PASSWORD_64_REDIS. Saisir séparément le mot de passe administrateur initial. Ne pas publier `.env` ou `.env.production`. `APP_FRONTEND_ORIGIN` doit être le domaine HTTPS réel du frontend, sans port interne ni barre finale.

## Persistance

- `postgres-data:/var/lib/postgresql` : données métier, comptes, pièces jointes et lectures.
- `redis-data:/data` : sessions, avec journal AOF.

Les volumes sont conservés lors du remplacement des conteneurs. Ne pas supprimer les volumes ou la ressource pour mettre à jour le code. Le script `scripts/backup-postgres.sh` effectue une sauvegarde PostgreSQL au format pg_dump. Le lancer sur le serveur avec le nom du projet Compose Coolify et son fichier Compose réels. Configurer son exécution quotidienne, une copie hors serveur et tester la restauration dans une base distincte. La première installation crée une base vide ; elle ne transfère pas les données de développement.

Le backend démarre après les contrôles de santé des bases et applique Flyway. Aucun import FACNO n’est déclenché au démarrage ; le cron prend ensuite le relais.

Publier `Dockerfile`, `.dockerignore` et `docker-compose.production.yml` avant de lancer le déploiement. Déployer le backend avant le frontend. Les Dockerfiles sont construits avec Java 25 et Next.js standalone.

## État du déploiement

La version applicative et les fichiers de déploiement sont publiés sur `main` dans les deux dépôts. Les mots de passe PostgreSQL et Redis seront générés par Coolify. Le mot de passe administrateur initial est une variable de production privée, jamais une valeur dans Git.

Déploiement vérifié le 7 novembre 2026 dans `TOMBOLA / production` : backend `kosvtovbg3uqgnpjzhetk6mn`, frontend `yl0jdaj9bukjf7frcc00htkg`. Les deux applications sont saines. Le frontend est disponible sur `https://signalements.72.62.16.125.sslip.io` avec un certificat valide. La connexion administrateur et les pages Signalements, Indicateurs, Imports et Comptes ont été vérifiées en HTTPS.

Le backend utilise le mode Docker Compose géré par Coolify, fichier `/docker-compose.production.yml`, sans domaine ni port public. Ne pas activer la connexion automatique de tous les services au réseau prédéfini : seul le backend rejoint explicitement `coolify`. Les alias `signalements-postgres` et `signalements-redis` évitent les conflits DNS avec les autres applications du serveur. Les volumes existants sont `kosvtovbg3uqgnpjzhetk6mn_postgres-data` et `kosvtovbg3uqgnpjzhetk6mn_redis-data`. Les 36 migrations sont validées au démarrage. La tâche temporaire de diagnostic a été supprimée.

FACNO est configuré et le planning d’import est activé. Les paramètres optionnels Mistral et Herdys ne sont pas renseignés ; aucune valeur n’était fournie localement. Les sauvegardes automatiques hors serveur restent à configurer.

Validation locale : images frontend/backend construites ; quatre services sains dans une stack isolée ; communication frontend → backend par DNS Docker ; données PostgreSQL/Redis conservées après remplacement des conteneurs ; script de sauvegarde PostgreSQL vérifié. La stack de test est supprimée. Les 121 tests backend passent.
