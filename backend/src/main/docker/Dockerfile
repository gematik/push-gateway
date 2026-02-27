ARG SOURCE_DIGEST=9323904961b9adac73c36c9771a187ecea21111d96a331eec6173b9649481243

FROM maven:3.9-eclipse-temurin-21-alpine AS dependencies

RUN mvn org.apache.maven.plugins:maven-dependency-plugin:${maven-dependency-plugin.version}:copy \
    -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:${opentelemetry-instrumentation.version}:jar \
    -DoutputDirectory=/tmp

FROM gematik1/osadl-alpine-openjdk21-jre:1.0.8@sha256:$SOURCE_DIGEST AS build

# Declare Source Digest for the Base Image
ARG SOURCE_DIGEST

ARG UID=10001
ARG GID=10001

HEALTHCHECK --interval=60s --timeout=2s \
    CMD wget --no-verbose --tries=3 -O - 'http://localhost:8081/actuator/health/liveness' || exit 1

USER "$UID:$GID"

WORKDIR /app

COPY --from=dependencies --chown=$UID:$GID --chmod=0644 /tmp/opentelemetry-javaagent-*.jar /app/opentelemetry-javaagent.jar
COPY --chown=$UID:$GID --chmod=0644 ./backend-*-standalone.jar /app/pushgateway.jar
COPY --chown=$UID:$GID --chmod=0755 ./entrypoint.sh /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]

ARG COMMIT_HASH
ARG VERSION

LABEL de.gematik.vendor="gematik GmbH" \
      maintainer="software-development@gematik.de" \
      de.gematik.app="Gematik TI Push-Gateway" \
      de.gematik.git-repo-name="https://github.com/gematik/push-gateway.git" \
      de.gematik.commit-sha=$COMMIT_HASH \
      de.gematik.version=$VERSION \
      de.gematik.source.digest=$SOURCE_DIGEST
