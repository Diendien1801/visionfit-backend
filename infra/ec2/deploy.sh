#!/usr/bin/env bash
# Update the running backend to the latest main. Run on the server, from the repo folder:
#   ./infra/ec2/deploy.sh
# The jars are built here, inside a memory-limited JDK container (the host has 2 GB of RAM and no
# JDK); Gradle's downloads stay in the visionfit-gradle volume, so only the first build is slow.
set -euo pipefail
cd "$(dirname "$0")/../.."
COMPOSE="docker compose -f docker-compose.prod.yml"

git pull -q --ff-only
echo "== Building jars ($(git log --oneline -1))"
docker run --rm -m 1500m \
    -v "$PWD":/w -v visionfit-gradle:/root/.gradle -w /w \
    -e GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx700m -Dorg.gradle.workers.max=1" \
    eclipse-temurin:21-jdk sh ./gradlew --no-daemon -q bootJar
mkdir -p dist
for s in identity-service profile-service meal-service analysis-service gateway-service; do
    cp "$s/build/libs/$s-0.0.1-SNAPSHOT.jar" "dist/$s.jar"
done

echo "== Restarting what changed"
$COMPOSE up -d --build --remove-orphans
docker image prune -f > /dev/null
$COMPOSE ps
