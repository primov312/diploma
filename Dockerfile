# 1) Build stage: use Maven with Eclipse Temurin JDK 17
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy only what we need for a fast build
COPY pom.xml .
COPY src ./src

# Build the Spring Boot fat JAR
RUN mvn clean package -DskipTests

# 2) Runtime stage: slim JDK 17
FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copy the JAR from the builder
COPY --from=builder /app/target/user-data-0.1.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]
