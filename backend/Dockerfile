FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:21.0.8_9-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home shortener
WORKDIR /app
COPY --from=build --chown=shortener:shortener /workspace/target/shortener.jar ./app.jar
USER shortener
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
