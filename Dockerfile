# Gestión (Spring Boot 3, Java 21). Construir: docker build -t condohub-gestion services/gestion
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Las pruebas de integración usan Testcontainers: se corren en CI, no al construir la imagen
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /srv
COPY --from=build /build/target/gestion-*.jar app.jar
RUN useradd --system --uid 10001 condohub
USER condohub
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8081'
# Configuración por entorno: GESTION_DB_URL, GESTION_DB_USER, GESTION_DB_PASSWORD, KEYCLOAK_ISSUER, KEYCLOAK_JWKS,
# SPRING_PROFILES_ACTIVE (dev carga los datos de demostración)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]