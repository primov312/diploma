FROM eclipse-temurin:17
WORKDIR /app
COPY --from=builder /app/target/user-data-0.1.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
