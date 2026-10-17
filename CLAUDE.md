# CLAUDE.md — condohub-gestion

Microservicio **Gestión empresarial** de CondoHub: contexto delimitado **Finanzas** (unidades, propietarios, presupuesto, expensas, cargos, pagos, morosidad, proveedores). Responsable: **Aldana**.

Este repo es independiente. Si está dentro del espacio de trabajo `condohub`, las reglas completas están en `../../docs/negocio/reglas-de-negocio.md` y el contrato en `../../contracts/graphql/schema.graphql`.

## Stack

Java 21 · Spring Boot 3 · Spring for GraphQL · Spring Data JPA · PostgreSQL 16 · Flyway · Spring AMQP (RabbitMQ) · Spring Security OAuth2 Resource Server (Keycloak) · JUnit 5 · AssertJ · Testcontainers · ArchUnit.

## Arquitectura: Hexagonal (puertos y adaptadores)

```
src/main/java/com/condohub/gestion/
├── domain/                     # PURO: sin Spring, JPA, Jackson ni nada de infraestructura
│   ├── model/                  # agregados: Unidad, CuentaUnidad, PeriodoExpensas, Pago
│   ├── valueobject/            # Dinero, Alicuota, Periodo, IdempotencyKey
│   ├── event/                  # ExpensasEmitidas, CargoAplicado, CargoAnulado, PagoConfirmado, RecargoPropuesto...
│   ├── service/                # CalculadoraExpensas, ImputadorPagos, CalculadoraRecargos
│   └── exception/              # ReglaNegocioVioladaException(codigo, mensaje)
├── application/
│   ├── port/in/                # casos de uso como interfaces: EmitirExpensasUseCase, ConfirmarPagoUseCase...
│   ├── port/out/               # UnidadRepository, CuentaUnidadRepository, EventPublisher, Clock
│   └── usecase/                # implementaciones; orquestan dominio + puertos, manejan la transacción
└── adapter/
    ├── in/graphql/             # controllers @QueryMapping/@MutationMapping → llaman port/in
    ├── in/messaging/           # consumidores RabbitMQ (paso 2 de la saga)
    ├── out/persistence/        # entidades JPA + mappers + implementaciones de port/out
    ├── out/messaging/          # Outbox: tabla outbox + publicador programado
    └── config/                 # beans, seguridad, wiring de puertos
```

Reglas de dependencia (las verifica un test ArchUnit, no lo borres):
- `domain` no depende de nada fuera de `domain` ni de `java.*`.
- `application` depende solo de `domain`.
- `adapter` depende de `application` y `domain`; nunca al revés.
- Las entidades JPA viven en `adapter/out/persistence` y se mapean a/desde el dominio. **No anotes el dominio con `@Entity`.**

## Reglas de negocio que implementa

| Código | Resumen |
|---|---|
| F1 | Una unidad: un propietario titular, 0..n residentes; solo ellos ven su estado de cuenta |
| F2 | Suma de alícuotas activas = 100 %, si no, no se emiten expensas |
| F3 | Emisión día 1: expensa = presupuesto × alícuota, 2 decimales, el redondeo va a la unidad de mayor alícuota |
| F4 | Vence el día 10; impago al 11 = vencido y la unidad pasa a morosa |
| F5 | Recargo 2 % mensual simple sobre saldo vencido; se **propone** y solo se aplica con confirmación del `ADMIN` |
| F6 | Cargos inmutables: correcciones con nota de crédito o cargo de ajuste con motivo |
| F7 | Pago confirmado se imputa FIFO a los cargos vencidos más antiguos; excedente = saldo a favor |
| F8 | Pago `PENDIENTE` → `CONFIRMADO` o `RECHAZADO` (con motivo); recibo solo al confirmar |
| F9 | Ningún pago se confirma offline ni desde el cliente |
| F10 | Dinero en `BigDecimal` / `NUMERIC(12,2)` / string en JSON |
| F11 | Proveedores y egresos se registran; sin flujo de aprobación |
| R4 (soporte) | Expone `morosidad(unidadId)` para que Reservas bloquee a unidades con ≥ 2 expensas vencidas |
| S2–S5 (paso 2 de la saga) | Consume "aplicar cargos de reserva" y "anular cargos de reserva", idempotente por `eventId` |

Los porcentajes y días son parámetros (tabla `parametro`), no constantes.

## API (GraphQL, único del sistema)

- Contrato: `contracts/graphql/schema.graphql` del repo `condohub`, copiado en `src/main/resources/graphql/`. Cambia primero el contrato.
- Queries: `estadoDeCuenta(unidadId)`, `morosos(periodo, bloque)`, `unidades`, `pagosPendientes`.
- Mutations: `emitirExpensas`, `registrarPago` (requiere `idempotencyKey`), `confirmarPago`, `rechazarPago`, `proponerRecargo`, `confirmarRecargo`, `registrarEgreso`.
- Autorización por rol en cada resolver: `RESIDENTE` solo su unidad; mutations contables solo `ADMIN`.
- Evita N+1 con `@BatchMapping`.
- Las queries deben servir también a reportes por voz (Servicio IA) y al agente: **solo lectura** para ellos.

## Eventos

Publica (vía Outbox): `ExpensasEmitidas`, `CargoAplicado`, `CargoAnulado`, `PagoRegistrado`, `PagoConfirmado`, `PagoRechazado`, `RecargoPropuesto`, `RecargoConfirmado`.
Consume: `reservas.AplicarCargosReserva`, `reservas.AnularCargosReserva` (tabla `evento_procesado` para idempotencia).

## Comandos

```bash
./mvnw spring-boot:run                 # puerto 8081 (requiere infra de condohub levantada)
./mvnw test                            # unitarios de dominio + ArchUnit
./mvnw verify                          # + integración con Testcontainers
./mvnw spotless:apply                  # formato
```

GraphiQL en dev: `http://localhost:8081/graphiql`.

## Convenciones locales

- Tests de dominio en `src/test/java/.../domain`, nombrados con la regla: `F7_pago_se_imputa_al_cargo_vencido_mas_antiguo()`.
- Un caso de uso = una clase = una transacción (`@Transactional` solo en `application/usecase`).
- Métodos con intención en los agregados (`pago.confirmar(admin)`, `cuenta.imputar(pago)`); nada de setters públicos.
- Migraciones Flyway `V<n>__descripcion.sql`; nunca edites una migración ya mergeada.
- Base de datos: `src/main/resources/db/migration` (esquema y vistas), `db/seed/R__datos_demo.sql` (solo perfil `dev`), pruebas de reglas en `src/test/resources/db/pruebas_reglas_bd.sql`. Diseño completo en `docs/datos/modelo-de-datos.md` del repo `condohub`. Las restricciones y triggers de la base (F3, F5–F8) son la última defensa: el dominio valida primero y nunca se apoya solo en ellos.

## Estado y seguridad (actualizado 16/10/2026)

Hecho: `salud` (cualquier rol), `estadoDeCuenta(unidadId)` (ADMIN todas; RESIDENTE solo su unidad, F1; GUARDIA no), `pagosPendientes` (ADMIN), `miPerfil` (cualquier rol: roles del token y, si la cuenta está enlazada a una persona, su `personaId` y unidades), mutation `enlazarCuenta(personaId, keycloakSub)` (ADMIN; enlaza la cuenta de Keycloak con la persona, F1) y `morosos` (ADMIN, incluida la cuenta de servicio de n8n y Agentes: unidades morosas con el contacto del titular). Todo lo demás está por hacer (emisión de expensas, cargos, pagos, recargos, proveedores, outbox y saga). Contrato mínimo en `contracts/graphql/schema.graphql`.

- JWT de Keycloak: `adapter/config/SeguridadConfig.java` (`realm_access.roles` → `ROLE_*`; 401 en problem+json). Rol por resolver con `@PreAuthorize`; la regla por dato (F1) vive en `domain/service/PoliticaVisibilidad`. El vínculo token → unidad es `persona.keycloak_sub`.
- Errores GraphQL: `extensions.code` = regla (`F1`), `FORBIDDEN` o `NOT_FOUND` (`GraphQlErrorResolver`).
- Correr: `mvn spring-boot:run -Dspring-boot.run.profiles=dev` (el perfil `dev` carga las 200 unidades). `mvn test`: 24 pruebas unitarias; `mvn verify`: además 16 de integración con Testcontainers (necesita Docker). Imagen: `Dockerfile` multi-stage; CI en `.github/workflows/ci.yml`. Aún no hay `mvnw` ni spotless.
- Variables: `GESTION_DB_URL`, `GESTION_DB_USER`, `GESTION_DB_PASSWORD`, `KEYCLOAK_ISSUER`, `KEYCLOAK_JWKS`. Con el 5432 ocupado: compose con `POSTGRES_PORT=5433` y `GESTION_DB_URL=jdbc:postgresql://localhost:5433/gestion`.
- Por el gateway: `POST http://localhost:8080/gestion/graphql` con `Authorization: Bearer <token>`.

## Código funcional protegido (obligatorio)

Código en `main` con tests en verde **no se reescribe, refactoriza, renombra ni reformatea** si la tarea no lo pide explícitamente. Lo nuevo se agrega extendiendo (clases, métodos o archivos nuevos). Si es inevitable tocar código funcional, **detente antes de editar**, explica qué archivo/líneas y por qué, y espera confirmación; luego haz el mínimo diff. Nunca edites tests existentes para que pase código nuevo. Corre los tests antes y después: todo lo que pasaba debe seguir pasando. En el resumen, lista cada archivo existente que modificaste.
