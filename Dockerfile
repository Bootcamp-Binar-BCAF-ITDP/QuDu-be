FROM eclipse-temurin:25-jdk AS build

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

FROM eclipse-temurin:25-jre

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 1001 qudu \
 && useradd --system --uid 1001 --gid qudu --home /app qudu

WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

RUN mkdir -p /app/uploads && chown -R qudu:qudu /app
VOLUME ["/app/uploads"]

USER qudu

ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["sh", "-c", "curl -fsS http://localhost:${PORT}/api/plafonds/catalog > /dev/null || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=$PORT -jar app.jar"]
