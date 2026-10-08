#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1

# Docker socket access is required to run sibling PostgreSQL test containers.
repo_root=$(cd "$(dirname "$0")/.." && pwd)
if command -v cygpath >/dev/null 2>&1; then
  repo_root=$(cygpath -m "$repo_root")
fi
docker run --rm \
  --mount "type=bind,source=$repo_root/backend,target=/workspace" \
  --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock \
  --mount type=volume,source=url-shortener-maven-cache,target=/root/.m2 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /workspace maven:3.9.11-eclipse-temurin-21 mvn -B -ntp -Pintegration verify
