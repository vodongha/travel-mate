# TravelMate API (trippo.io.vn) — serves the bundled Flutter web client at "/" and the API under
# "/api/v1", so the browser client is same-origin (no CORS). The web build is produced by CI from
# the (private) travel-mate-app repo and dropped into ./web-dist before the image is built; it gets
# baked into the Spring static resources. For an API-only build, web-dist holds just a .gitkeep.

# ---- Stage 1: build the Spring Boot jar (web client baked into static) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first for layer caching.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# The prebuilt Flutter web client (CI places it here; .gitkeep keeps the path valid otherwise).
COPY web-dist/ ./src/main/resources/static/
RUN mvn -B -q clean package -Dmaven.test.skip=true

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar
COPY scripts/fly_entrypoint.sh ./scripts/fly_entrypoint.sh
RUN chmod +x ./scripts/fly_entrypoint.sh

EXPOSE 8000

# The entrypoint materialises the Oracle ADB wallet from Fly secrets (if present), then runs the jar.
ENTRYPOINT ["./scripts/fly_entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]
