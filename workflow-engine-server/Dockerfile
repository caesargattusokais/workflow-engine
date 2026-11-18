# Workflow engine server — run the prebuilt Spring Boot jar.
# Build the jar first from the repo root:
#   mvn -pl workflow-engine-server -am package -DskipTests
# (kept single-stage on purpose: a multi-stage Maven build re-downloads
# all dependencies on every image build)
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/workflow-engine-server-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
