package com.condohub.gestion.adapter.out.messaging;

import com.condohub.gestion.application.port.out.RespuestasSaga;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * S4: guarda las respuestas a la saga en la tabla {@code outbox} con la transacción en curso; {@link OutboxRelay} las
 * envía con el {@code sagaId} del comando, para que el orquestador de Reservas las correlacione. El {@code eventId} se
 * deriva del comando que las originó: el mismo comando siempre produce la misma respuesta (S5).
 */
@Component
class RespuestasSagaOutbox implements RespuestasSaga {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;

    RespuestasSagaOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void cargoAplicado(UUID comandoEventId, UUID sagaId, UUID reservaId, UUID unidadId, List<UUID> cargoIds) {
        guardar("finanzas.CargoAplicado", comandoEventId, sagaId, reservaId, unidadId, cargoIds);
    }

    @Override
    public void cargoAnulado(UUID comandoEventId, UUID sagaId, UUID reservaId, UUID unidadId, List<UUID> cargoIds) {
        guardar("finanzas.CargoAnulado", comandoEventId, sagaId, reservaId, unidadId, cargoIds);
    }

    /** eventId de la respuesta a un comando: siempre el mismo para el mismo comando. */
    static UUID eventIdDeRespuesta(String tipo, UUID comandoEventId) {
        return UUID.nameUUIDFromBytes((tipo + ":" + comandoEventId).getBytes(StandardCharsets.UTF_8));
    }

    /** {@code data} del contrato (contracts/events/finanzas.CargoAplicado / CargoAnulado). */
    static String datosJson(UUID reservaId, UUID unidadId, List<UUID> cargoIds) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("reservaId", reservaId.toString());
        datos.put("unidadId", unidadId.toString());
        datos.put("cargoIds", cargoIds.stream().map(UUID::toString).toList());
        try {
            return JSON.writeValueAsString(datos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la respuesta a la saga", e);
        }
    }

    private void guardar(String tipo, UUID comandoEventId, UUID sagaId, UUID reservaId, UUID unidadId, List<UUID> cargoIds) {
        jdbc.update(
                """
                INSERT INTO outbox (id, aggregate_type, aggregate_id, event_type, routing_key, saga_id, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id) DO NOTHING
                """,
                eventIdDeRespuesta(tipo, comandoEventId),
                "Reserva",
                reservaId.toString(),
                tipo.substring(tipo.indexOf('.') + 1),
                tipo,
                sagaId,
                datosJson(reservaId, unidadId, cargoIds));
    }
}
