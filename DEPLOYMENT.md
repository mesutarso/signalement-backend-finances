# Backend et données — Coolify

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

## État de la préparation Coolify

Une ressource backend a été créée dans `TOMBOLA / production` : `kosvtovbg3uqgnpjzhetk6mn`. Aucun déploiement n’a été lancé. L’interface n’affichant plus le contenu des réglages, le chemin Compose de production n’a pas pu être confirmé après création. **Vérifier `/docker-compose.production.yml` avant tout déploiement ; ne pas utiliser le Compose de développement.** La ressource frontend et son domaine temporaire restent à finaliser. Les fichiers de préparation sont locaux et ne sont pas encore publiés dans Git.

Validation locale : images frontend/backend construites, quatre services sains dans une stack isolée, appel frontend → backend par DNS Docker, données PostgreSQL/Redis conservées après remplacement des conteneurs et sauvegarde PostgreSQL créée avec le script fourni. La stack de test est ensuite supprimée.
