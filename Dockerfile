FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY rocket-credit-claimcheck/pom.xml rocket-credit-claimcheck/pom.xml
COPY rocket-credit-claimcheck/src     rocket-credit-claimcheck/src
RUN mvn -f rocket-credit-claimcheck/pom.xml -DskipTests clean install

COPY rocket-credit-user-data/pom.xml rocket-credit-user-data/pom.xml
COPY rocket-credit-user-data/src     rocket-credit-user-data/src
WORKDIR /workspace/rocket-credit-user-data
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/rocket-credit-user-data/target/user-data-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]