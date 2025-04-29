###############################################################################
# Build-and-run image for dockerpoc-1
# - OpenJDK 17 (Azul Zulu, JRE–only)
# - Optional curl
# - JDWP remote-debug on $DEBUG_PORT (default 5005)
###############################################################################
FROM azul/zulu-openjdk:17-jre

# ─── metadata ────────────────────────────────────────────────────────────────
LABEL org.opencontainers.image.title="dockerpoc-1" \
      org.opencontainers.image.base.name="azul/zulu-openjdk:17-jre" \
      org.opencontainers.image.source="https://github.com/<your-repo>" \
      maintainer="vishal210893@gmail.com"

# ─── runtime deps (curl is optional) ─────────────────────────────────────────
RUN set -eux; \
    apt-get update -qq && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# ─── directories ─────────────────────────────────────────────────────────────
WORKDIR /opt/aims
RUN mkdir -p /tmp /opt/file
VOLUME /tmp /opt/aims /opt/file

# ─── copy assets ─────────────────────────────────────────────────────────────
ARG JAR_FILE=target/dockerpoc-1.jar
COPY ${JAR_FILE} app.jar
COPY nginx/ /tmp/

# ─── ports ───────────────────────────────────────────────────────────────────
EXPOSE 8005

ENV DEBUG_PORT=5005
EXPOSE ${DEBUG_PORT}

# Add the JDWP agent options via JAVA_TOOL_OPTIONS
# - transport=dt_socket
# - server=y
# - suspend=n
# - address=*:$DEBUG_PORT

# ─── JVM tuning & debug switch ───────────────────────────────────────────────
ENV JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT}" \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"

# ─── run ─────────────────────────────────────────────────────────────────────
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
