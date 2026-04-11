FROM clojure:lein AS builder
WORKDIR /app
COPY project.clj .
RUN lein deps
COPY src/ src/
RUN lein uberjar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/uberjar/spenser-server-0.1.0-SNAPSHOT-standalone.jar app.jar
RUN mkdir -p data
CMD ["java", "-jar", "app.jar"]
