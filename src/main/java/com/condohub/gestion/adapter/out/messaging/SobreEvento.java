package com.condohub.gestion.adapter.out.messaging;

import com.condohub.gestion.domain.event.EventoFinanzas;
import com.condohub.gestion.domain.event.PagoConfirmado;
import com.condohub.gestion.domain.event.PagoRegistrado;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Traduce los eventos de dominio al contrato de {@code contracts/events}: el sobre común
 * {@code {eventId, type, occurredAt, version, data}} y el {@code data} de cada evento (dinero como string, F10).
 */
final class SobreEvento {

    /** Versión del esquema de {@code data} de los eventos de Finanzas publicados. */
    static final int VERSION = 1;

    private static final ObjectMapper JSON = new ObjectMapper();

    private SobreEvento() {}

    /**
     * El mismo evento del mismo agregado siempre tiene el mismo {@code eventId}: si se registra dos veces, el Outbox
     * lo guarda una sola y los consumidores lo descartan por {@code eventId} (S5).
     */
    static UUID eventId(EventoFinanzas evento) {
        return UUID.nameUUIDFromBytes((evento.tipo() + ":" + evento.agregadoId()).getBytes(StandardCharsets.UTF_8));
    }

    /** Nombre del evento sin el contexto, ej. {@code PagoRegistrado}. */
    static String nombre(EventoFinanzas evento) {
        return evento.tipo().substring(evento.tipo().indexOf('.') + 1);
    }

    static Map<String, Object> datos(EventoFinanzas evento) {
        Map<String, Object> datos = new LinkedHashMap<>();
        switch (evento) {
            case PagoRegistrado e -> {
                datos.put("pagoId", e.pagoId().toString());
                datos.put("unidadId", e.unidadId().toString());
                datos.put("monto", e.monto().toString());
                datos.put("fechaPago", e.fechaPago().toString());
                if (e.banco() != null) {
                    datos.put("banco", e.banco());
                }
            }
            case PagoConfirmado e -> {
                datos.put("pagoId", e.pagoId().toString());
                datos.put("unidadId", e.unidadId().toString());
                datos.put("monto", e.monto().toString());
                datos.put("reciboNumero", String.format("%06d", e.reciboNumero()));
            }
        }
        return datos;
    }

    static String datosJson(EventoFinanzas evento) {
        try {
            return JSON.writeValueAsString(datos(evento));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar " + evento.tipo(), e);
        }
    }

    /** El mensaje completo que viaja por RabbitMQ. */
    static String sobre(UUID eventId, String tipo, Instant ocurrido, UUID sagaId, String datosJson) {
        try {
            ObjectNode sobre = JSON.createObjectNode();
            sobre.put("eventId", eventId.toString());
            sobre.put("type", tipo);
            sobre.put("occurredAt", ocurrido.truncatedTo(ChronoUnit.MILLIS).toString());
            if (sagaId != null) {
                sobre.put("sagaId", sagaId.toString());
            }
            sobre.put("version", VERSION);
            sobre.set("data", JSON.readTree(datosJson));
            return JSON.writeValueAsString(sobre);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Evento del outbox con data inválida: " + eventId, e);
        }
    }
}
