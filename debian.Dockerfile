FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY . .
RUN ./gradlew installDist
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/install/cdd-kotlin /app/cdd-kotlin
EXPOSE 8082
ENTRYPOINT ["/app/cdd-kotlin/bin/cdd-kotlin", "serve_json_rpc", "--port", "8082", "--listen", "0.0.0.0"]
