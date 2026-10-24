#!/usr/bin/env bash
# Update the running backend to the latest main. Run on the server, from the repo folder:
#   ./infra/ec2/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

git pull --ff-only
docker compose -f docker-compose.prod.yml up -d --build --remove-orphans
docker image prune -f > /dev/null
docker compose -f docker-compose.prod.yml ps
