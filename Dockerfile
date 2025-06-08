FROM eclipse-temurin:17

WORKDIR /app

COPY target/gateway-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
