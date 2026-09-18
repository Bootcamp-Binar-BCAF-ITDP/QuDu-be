FROM eclipse-temurin:25-jdk AS build

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -Dmaven.test.skip=true \
 && mv target/*.jar target/app.jar

FROM eclipse-temurin:25-jre AS runtime

LABEL org.opencontainers.image.title="QuDu-be" \
      org.opencontainers.image.description="QuickDuit loan backend" \
      org.opencontainers.image.source="https://github.com/Bootcamp-Binar-BCAF-ITDP/QuDu-be"

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 1001 qudu \
 && useradd --system --uid 1001 --gid qudu --home /app qudu

WORKDIR /app

COPY --from=build --chown=qudu:qudu /build/target/app.jar app.jar

RUN mkdir -p /app/uploads /app/secrets && chown -R qudu:qudu /app

USER qudu

ENV PORT=8080 \
    UPLOAD_ROOT=/app/uploads \
    SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS "http://localhost:${PORT}/api/plafonds/catalog" > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=$PORT -jar app.jar"]
