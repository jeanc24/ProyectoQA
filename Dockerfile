# BUILD
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# Cache de dependencias
COPY src src
# Un solo artefacto ejecutable (evita COPY *.jar que mezcla boot + -plain → jar corrupto)
RUN ./gradlew bootJar -x test --no-daemon \
  && BOOT_JAR="$(ls build/libs/*.jar | grep -v 'plain' | head -n 1)" \
  && cp "$BOOT_JAR" /app/application.jar

# RUNTIME
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY --from=build /app/application.jar app.jar
COPY scripts/docker-entrypoint-api.sh /app/docker-entrypoint-api.sh
RUN chmod +x /app/docker-entrypoint-api.sh

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=90s \
  CMD curl -f "http://localhost:${PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["/app/docker-entrypoint-api.sh"]
