#!/bin/sh

exec java $JAVA_OPTS -javaagent:/app/opentelemetry-javaagent.jar -Dsun.security.ssl.allowLegacyHelloMessages=false -Dlog4j2.formatMsgNoLookups=true -jar /app/pushgateway.jar
