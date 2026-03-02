FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app
COPY . /app

# Install dependencies and build
RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/install/cdd-kotlin /app/

EXPOSE 8082
ENTRYPOINT ["/app/bin/cdd-kotlin", "serve_json_rpc", "--port", "8082", "--listen", "0.0.0.0"]
