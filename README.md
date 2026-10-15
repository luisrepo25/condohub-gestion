# condohub-gestion

Gestión empresarial de CondoHub (contexto Finanzas). Spring Boot 3 · Java 21 · GraphQL · PostgreSQL · Hexagonal.

```bash
docker compose -f infra/docker-compose.yml up -d postgres keycloak   # desde la raíz de condohub
mvn spring-boot:run -Dspring-boot.run.profiles=dev                  # puerto 8081, con datos de demostración
mvn verify                                                           # unitarias + integración (Testcontainers, necesita Docker)
```

Arquitectura, reglas, estado, variables y puertos: `CLAUDE.md`.