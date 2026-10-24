# syntax=docker/dockerfile:1
# Runtime image of one app. The jar is built on the laptop (./gradlew bootJar) and placed in dist/
# by infra/ec2/deploy.ps1, so the 2 GB server never runs Gradle.
#   docker build --build-arg SERVICE=identity-service -t visionfit/identity-service .
FROM eclipse-temurin:21-jre
ARG SERVICE
# Not root: a hole in the app must not hand out root inside the container.
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY dist/${SERVICE}.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
