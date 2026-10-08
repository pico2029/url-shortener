#!/usr/bin/env bash
set -euo pipefail
# Keep container paths intact when invoking docker.exe from Git Bash on Windows.
export MSYS_NO_PATHCONV=1

# Run after docker compose up --build --wait. No host language runtimes required.
docker compose config --quiet
docker compose exec -T backend curl --fail --silent http://localhost:8080/actuator/health | grep -F '"status":"UP"'
docker compose exec -T frontend wget -qO- http://127.0.0.1/ | grep -F '<title>URL Shortener</title>'
docker compose exec -T frontend sh -c 'grep -q "Shorten URL" /usr/share/nginx/html/assets/*.js'

response=$(docker compose exec -T backend curl --fail --silent -H 'Content-Type: application/json' --data '{"originalUrl":"https://example.com/us01?verified=true#fragment"}' http://frontend/api/v1/links)
code=$(printf '%s' "$response" | sed -n 's/.*"shortCode":"\([A-Za-z0-9]\{8\}\)".*/\1/p')
test -n "$code"
printf '%s' "$response" | grep -F "/r/$code"
count_before=$(docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT count(*) FROM links"')
status=$(docker compose exec -T backend curl --silent --output /dev/null --write-out '%{http_code}' -H 'Content-Type: application/json' --data '{"originalUrl":"ftp://example.com"}' http://frontend/api/v1/links)
test "$status" = 400
count_after=$(docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT count(*) FROM links"')
test "$count_before" = "$count_after"

for path in /api/v1/links /r/example /swagger-ui/index.html /v3/api-docs; do
  status=$(docker compose exec -T backend curl --silent --output /dev/null --write-out '%{http_code}' -H 'Accept: application/json' "http://frontend$path")
  test "$status" = 401 || { echo "Unexpected status for $path: $status"; exit 1; }
done
status=$(docker compose exec -T backend curl --silent --output /dev/null --write-out '%{http_code}' http://frontend/actuator/health)
test "$status" = 404

# Container recreation must preserve both the PostgreSQL cluster and the new link.
cluster_before=$(docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT system_identifier FROM pg_control_system()"')
docker compose up -d --force-recreate --wait --wait-timeout 180
cluster_after=$(docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT system_identifier FROM pg_control_system()"')
test "$cluster_before" = "$cluster_after"
stored=$(docker compose exec -T postgres psql -U "$(docker compose exec -T postgres printenv POSTGRES_USER)" -d "$(docker compose exec -T postgres printenv POSTGRES_DB)" -Atc "SELECT original_url FROM links WHERE short_code = '$code'")
test "$stored" = 'https://example.com/us01?verified=true#fragment'
docker compose exec -T backend curl --fail --silent http://localhost:8080/actuator/health | grep -F '"status":"UP"'
echo "US01 checks passed; PostgreSQL link preserved."
