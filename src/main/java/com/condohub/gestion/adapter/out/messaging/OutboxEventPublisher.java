package com.condohub.gestion.adapter.out.messaging;

import com.condohub.gestion.application.port.out.EventPublisher;
import com.condohub.gestion.domain.event.EventoFinanzas;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * S4: guarda el evento en la tabla {@code outbox} con la conexión de la transacción en curso; si el caso de uso hace
 * rollback, el evento tampoco queda. {@link OutboxRelay} lo envía después a RabbitMQ.
 */
@Component
public class OutboxEventPublisher implements EventPublisher {

    private final JdbcTemplate jdbc;

    OutboxEventPublisher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void publicar(EventoFinanzas evento) {
        jdbc.update(
                """
                INSERT INTO outbox (id, aggregate_type, aggregate_id, event_type, routing_key, payload)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id) DO NOTHING
                """,
                SobreEvento.eventId(evento),
                "Pago",
                evento.agregadoId().toString(),
                SobreEvento.nombre(evento),
                evento.tipo(),
                SobreEvento.datosJson(evento));
    }
}
