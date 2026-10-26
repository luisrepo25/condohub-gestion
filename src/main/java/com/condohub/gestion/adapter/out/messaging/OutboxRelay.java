package com.condohub.gestion.adapter.out.messaging;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * S4: envía a RabbitMQ (exchange {@code condohub.events}, routing key = tipo) los eventos pendientes del outbox, en
 * orden, y los marca publicados solo cuando el broker confirma la recepción. Si RabbitMQ no responde, los eventos
 * esperan al siguiente intento: entrega al menos una vez; los consumidores descartan repetidos por eventId (S5).
 */
@Component
@ConditionalOnProperty(name = "condohub.outbox.relay.habilitado", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int LOTE = 50;
    private static final long ESPERA_CONFIRMACION_MS = 5_000;

    private record Pendiente(UUID id, String routingKey, UUID sagaId, Timestamp ocurrido, String datos) {}

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transaccion;
    private final String exchange;

    OutboxRelay(
            JdbcTemplate jdbc,
            RabbitTemplate rabbit,
            TransactionTemplate transaccion,
            @Value("${condohub.outbox.exchange:condohub.events}") String exchange) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.transaccion = transaccion;
        this.exchange = exchange;
    }

    @Scheduled(fixedDelayString = "${condohub.outbox.intervalo-ms:2000}")
    public void publicarPendientes() {
        transaccion.executeWithoutResult(estado -> {
            // SKIP LOCKED: si hay dos instancias del servicio, cada evento lo envía una sola.
            List<Pendiente> pendientes = jdbc.query(
                    """
                    SELECT id, routing_key, saga_id, occurred_at, payload::text AS payload FROM outbox
                     WHERE published_at IS NULL
                     ORDER BY occurred_at, id
                     LIMIT ?
                     FOR UPDATE SKIP LOCKED
                    """,
                    (rs, i) -> new Pendiente(
                            rs.getObject("id", UUID.class),
                            rs.getString("routing_key"),
                            rs.getObject("saga_id", UUID.class),
                            rs.getTimestamp("occurred_at"),
                            rs.getString("payload")),
                    LOTE);
            for (Pendiente evento : pendientes) {
                if (!enviar(evento)) {
                    // Se respeta el orden: el resto espera al próximo intento.
                    jdbc.update("UPDATE outbox SET intentos = intentos + 1 WHERE id = ?", evento.id());
                    return;
                }
                jdbc.update("UPDATE outbox SET published_at = now(), intentos = intentos + 1 WHERE id = ?", evento.id());
            }
        });
    }

    private boolean enviar(Pendiente evento) {
        String cuerpo = SobreEvento.sobre(
                evento.id(), evento.routingKey(), evento.ocurrido().toInstant(), evento.sagaId(), evento.datos());
        Message mensaje = MessageBuilder.withBody(cuerpo.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setContentEncoding("utf-8")
                .setMessageId(evento.id().toString())
                .setType(evento.routingKey())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        try {
            rabbit.invoke(operaciones -> {
                operaciones.send(exchange, evento.routingKey(), mensaje);
                operaciones.waitForConfirmsOrDie(ESPERA_CONFIRMACION_MS);
                return null;
            });
            return true;
        } catch (AmqpException e) {
            log.warn("RabbitMQ no confirmó el evento {} ({}); se reintenta luego: {}",
                    evento.id(), evento.routingKey(), e.getMessage());
            return false;
        }
    }
}
