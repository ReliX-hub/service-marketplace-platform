# -------- build stage --------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Cache Maven wrapper and dependencies separately for faster rebuilds
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q || true

# Copy source and build
COPY src src
RUN ./mvnw -q -DskipTests clean package

# -------- run stage --------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --chown=10001:10001 --from=build /app/target/service-marketplace-platform-0.0.1-SNAPSHOT.jar app.jar

RUN mkdir -p /var/marketplace/files \
    && chown -R 10001:10001 /var/marketplace/files

EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
