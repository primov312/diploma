# Build context is the repository root (see rocket-credit-deployment/docker-compose.diploma.yml).

# 1) Build the React app so Java can serve it from one origin.
FROM node:22-alpine AS web
WORKDIR /web
COPY demo-repository/package.json demo-repository/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY demo-repository/ ./
RUN npm run build

# 2) Build the Spring Boot jar with the built frontend in classpath:/static.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY rocket-credit-backend/pom.xml ./pom.xml
RUN mvn -q -B dependency:go-offline
COPY rocket-credit-backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# 3) Small runtime image, non-root.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 backend
COPY --from=build /workspace/target/rocket-credit-backend-*.jar app.jar
USER backend
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
