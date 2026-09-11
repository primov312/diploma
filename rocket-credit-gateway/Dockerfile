# ---------- build stage ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace

# build & install the claimcheck lib
COPY rocket-credit-claimcheck/pom.xml rocket-credit-claimcheck/pom.xml
COPY rocket-credit-claimcheck/src     rocket-credit-claimcheck/src
RUN mvn -f rocket-credit-claimcheck/pom.xml -DskipTests clean install

# build the gateway (now the dependency is resolvable)
COPY rocket-credit-gateway/pom.xml rocket-credit-gateway/pom.xml
COPY rocket-credit-gateway/src     rocket-credit-gateway/src
RUN mvn -f rocket-credit-gateway/pom.xml -DskipTests clean package

# ---------- runtime stage ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/rocket-credit-gateway/target/gateway-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]