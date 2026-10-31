#!/usr/bin/env bash
# Update the running backend to the latest main. Run on the server, from the repo folder:
#   ./infra/ec2/deploy.sh            the images CI built for the commit just pulled
#   ./infra/ec2/deploy.sh 1a2b3c4    the images of an older commit (rollback; first 7 characters)
# Nothing is built here: CI pushes the images to ghcr.io once the tests pass on main. If CI has not
# finished yet, the pull fails with "manifest unknown"; wait for the green check and run it again.
set -euo pipefail
cd "$(dirname "$0")/../.."
COMPOSE="docker compose -f docker-compose.prod.yml"

git pull -q --ff-only
export IMAGE_TAG="${1:-$(git rev-parse HEAD | cut -c1-7)}"
echo "== Deploying images $IMAGE_TAG"
$COMPOSE pull -q
$COMPOSE up -d --remove-orphans
# Older versions are only a cache: ghcr.io keeps every tag, so a rollback pulls it again.
docker image prune -af > /dev/null
$COMPOSE ps
