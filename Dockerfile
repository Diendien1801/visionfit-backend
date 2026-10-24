# syntax=docker/dockerfile:1
# One Dockerfile for all five apps:
#   docker build --build-arg SERVICE=identity-service -t visionfit/identity-service .
# The context is the repo root: the apps share settings.gradle and the Gradle wrapper.

# ---------- Stage 1: build the jar (needs the full JDK) ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
ARG SERVICE
# The cache mount keeps Gradle's downloads between builds, so only the first build fetches them.
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew --no-daemon :${SERVICE}:bootJar \
 && cp ${SERVICE}/build/libs/${SERVICE}-0.0.1-SNAPSHOT.jar /app.jar

# ---------- Stage 2: run it (only the JRE and the jar from stage 1) ----------
FROM eclipse-temurin:21-jre
# Not root: a hole in the app must not hand out root inside the container.
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
