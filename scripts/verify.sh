#!/usr/bin/env bash
set -euo pipefail

# Run after docker compose up --build --wait. No host language runtimes required.
docker compose config --quiet
docker compose exec -T backend curl --fail --silent http://localhost:8080/actuator/health | grep -F '"status":"UP"'
docker compose exec -T frontend wget -qO- http://127.0.0.1/ | grep -F '<title>URL Shortener</title>'
docker compose exec -T frontend sh -c 'grep -q "Struttura del progetto pronta" /usr/share/nginx/html/assets/*.js'

for path in /api/v1/links /r/example /swagger-ui/index.html /v3/api-docs; do
  status=$(docker compose exec -T backend curl --silent --output /dev/null --write-out '%{http_code}' -H 'Accept: application/json' "http://frontend$path")
  test "$status" = 401 || { echo "Unexpected status for $path: $status"; exit 1; }
done
status=$(docker compose exec -T backend curl --silent --output /dev/null --write-out '%{http_code}' http://frontend/actuator/health)
test "$status" = 404

# PostgreSQL's cluster identifier survives container recreation only with its data volume.
cluster_before=$(docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT system_identifier FROM pg_control_system()"')
docker compose up -d --force-recreate --wait --wait-timeout 180
cluster_after=$(docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT system_identifier FROM pg_control_system()"')
test "$cluster_before" = "$cluster_after"
docker compose exec -T backend curl --fail --silent http://localhost:8080/actuator/health | grep -F '"status":"UP"'
echo "Docker skeleton checks passed; PostgreSQL cluster preserved."
