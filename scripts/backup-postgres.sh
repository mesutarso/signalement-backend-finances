#!/usr/bin/env bash
set -euo pipefail
umask 077
project_root="$(cd "$(dirname "$0")/.." && pwd)"
backup_dir="${BACKUP_DIR:-$project_root/backups}"
mkdir -p "$backup_dir"
backup_file="$backup_dir/signalements-$(date -u +%Y%m%dT%H%M%SZ).dump"
backup_tmp="${backup_file}.partial"
trap 'rm -f "$backup_tmp"' EXIT
# Run from the server's actual Compose directory with its project name and environment.
# Example: COMPOSE_FILE=/path/to/docker-compose.yml COMPOSE_PROJECT_NAME=<coolify-stack> ./backup-postgres.sh
if [[ -z "${COMPOSE_FILE:-}" ]]; then
  export COMPOSE_FILE="$project_root/docker-compose.production.yml"
  export COMPOSE_ENV_FILES="${COMPOSE_ENV_FILES:-$project_root/.env.production}"
fi
docker compose exec -T postgres sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' > "$backup_tmp"
test -s "$backup_tmp"
mv "$backup_tmp" "$backup_file"
printf 'Sauvegarde créée : %s\n' "$backup_file"
