#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1

# Run after docker compose up --build --wait. Recreates application containers;
# the PostgreSQL volume and all existing accounts are retained.
docker compose config --quiet
docker compose exec -T backend curl --fail --silent http://localhost:8080/actuator/health | grep -F '"status":"UP"'
docker compose exec -T frontend wget -qO- http://127.0.0.1/ | grep -F '<title>URL Shortener</title>'
for page in register login dashboard; do
  docker compose exec -T frontend wget -qO- "http://127.0.0.1/$page" | grep -F '<title>URL Shortener</title>'
done
docker compose exec -T frontend sh -c 'grep -q "Crea un account" /usr/share/nginx/html/assets/*.js'

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

# A UUID keeps repeated runs independent. These synthetic accounts remain in the
# development database so persistence can be checked without deleting any data.
verification_id=$(docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT gen_random_uuid()"')
verification_email="verify-${verification_id}@example.com"
payload=$(printf '{"email":"%s","password":"verification password 2026"}' "$verification_email")
status=$(printf '%s' "$payload" | docker compose exec -T backend curl --silent --output /dev/null --write-out '%{http_code}' -H 'Content-Type: application/json' --data-binary @- http://frontend/api/v1/auth/register)
test "$status" = 201 || { echo "Registration failed: $status"; exit 1; }
status=$(printf '%s' "$payload" | docker compose exec -T backend curl --silent --output /dev/null --write-out '%{http_code}' -H 'Content-Type: application/json' --data-binary @- http://frontend/api/v1/auth/register)
test "$status" = 409 || { echo "Duplicate registration was not rejected: $status"; exit 1; }

account_snapshot() {
  printf "SELECT id, email, password_hash, created_at FROM users WHERE email = '%s';\n" "$verification_email" |
    docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'
}
account_before=$(account_snapshot)
test -n "$account_before"
case "$account_before" in
  *'{pbkdf2@SpringSecurity_v5_8}'*) ;;
  *) echo "Expected a PBKDF2 password hash"; exit 1 ;;
esac
cluster_before=$(docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT system_identifier FROM pg_control_system()"')
docker compose up -d --force-recreate --wait --wait-timeout 180
cluster_after=$(docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT system_identifier FROM pg_control_system()"')
test "$cluster_before" = "$cluster_after"
test "$account_before" = "$(account_snapshot)"
stored=$(printf "SELECT original_url FROM links WHERE short_code = '%s';\n" "$code" |
  docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At')
test "$stored" = 'https://example.com/us01?verified=true#fragment'
docker compose exec -T backend curl --fail --silent http://localhost:8080/actuator/health | grep -F '"status":"UP"'
echo "Link creation, registration, proxy and persistence checks passed."
