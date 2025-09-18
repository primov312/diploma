FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk
WORKDIR /app

COPY --from=builder /app/target/rocket-credit-repayment-0.1.0.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]
