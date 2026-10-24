# syntax=docker/dockerfile:1
# Runtime image of one app. The jar is built into dist/ by infra/ec2/deploy.sh (on the server, in a
# JDK container) or by hand with ./gradlew bootJar, so this image never needs Gradle.
#   docker build --build-arg SERVICE=identity-service -t visionfit/identity-service .
FROM eclipse-temurin:21-jre
ARG SERVICE
# Not root: a hole in the app must not hand out root inside the container.
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY dist/${SERVICE}.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
