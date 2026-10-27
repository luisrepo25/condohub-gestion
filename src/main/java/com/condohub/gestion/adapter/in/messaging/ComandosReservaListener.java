package com.condohub.gestion.adapter.in.messaging;

import com.condohub.gestion.application.port.in.AnularCargosReservaUseCase;
import com.condohub.gestion.application.port.in.AplicarCargosReservaUseCase;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.AlcanceAnulacion;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * Consumidor de la saga de reserva (paso 2): lee la cola {@code finanzas.reservas} y llama al caso de uso de cada
 * comando. Sin lógica de negocio: valida el sobre del contrato y traduce. Un mensaje que no cumple el contrato o que no
 * se puede cumplir (unidad inexistente) va a la DLQ sin reintentos; un fallo técnico (base caída) se reintenta y, si
 * persiste, también va a la DLQ (S2: la saga compensa por tiempo de espera). El bean lo crea {@code SagaReservasConfig}.
 */
public class ComandosReservaListener {

    private static final Logger log = LoggerFactory.getLogger(ComandosReservaListener.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AplicarCargosReservaUseCase aplicar;
    private final AnularCargosReservaUseCase anular;

    public ComandosReservaListener(AplicarCargosReservaUseCase aplicar, AnularCargosReservaUseCase anular) {
        this.aplicar = aplicar;
        this.anular = anular;
    }

    @RabbitListener(queues = "${condohub.saga.cola:finanzas.reservas}")
    public void recibir(Message mensaje) {
        JsonNode sobre = leer(mensaje);
        String tipo = sobre.path("type").asText();
        UUID eventId = uuid(sobre, "eventId");
        try {
            switch (tipo) {
                case "reservas.AplicarCargosReserva" -> {
                    var r = aplicar.aplicar(comandoAplicar(sobre, eventId));
                    log.info("AplicarCargosReserva {}: {}", eventId, r.procesado() ? r.cargoIds().size() + " cargo(s)" : "repetido, se descarta");
                }
                case "reservas.AnularCargosReserva" -> {
                    var r = anular.anular(comandoAnular(sobre, eventId));
                    log.info("AnularCargosReserva {}: {}", eventId, r.procesado() ? r.cargoIds().size() + " anulado(s)" : "repetido, se descarta");
                }
                default -> throw rechazo("tipo de mensaje no esperado en esta cola: " + tipo);
            }
        } catch (RecursoNoEncontradoException | ReglaNegocioVioladaException e) {
            throw rechazo(tipo + " " + eventId + " no se puede cumplir: " + e.getMessage());
        }
    }

    private static AplicarCargosReservaUseCase.Comando comandoAplicar(JsonNode sobre, UUID eventId) {
        JsonNode data = sobre.path("data");
        return new AplicarCargosReservaUseCase.Comando(
                eventId,
                uuidOpcional(sobre, "sagaId"),
                uuid(data, "reservaId"),
                uuid(data, "unidadId"),
                data.path("areaComun").isTextual() ? data.path("areaComun").asText() : null,
                dinero(data, "costoUso", true),
                dinero(data, "garantia", false));
    }

    private static AnularCargosReservaUseCase.Comando comandoAnular(JsonNode sobre, UUID eventId) {
        JsonNode data = sobre.path("data");
        AlcanceAnulacion alcance;
        try {
            alcance = AlcanceAnulacion.valueOf(data.path("alcance").asText());
        } catch (IllegalArgumentException e) {
            throw rechazo("alcance inválido: " + data.path("alcance").asText());
        }
        return new AnularCargosReservaUseCase.Comando(
                eventId, uuidOpcional(sobre, "sagaId"), uuid(data, "reservaId"), uuid(data, "unidadId"), alcance);
    }

    private static JsonNode leer(Message mensaje) {
        try {
            JsonNode sobre = JSON.readTree(mensaje.getBody());
            if (sobre == null || !sobre.isObject() || sobre.path("version").asInt() != 1 || !sobre.path("data").isObject()) {
                throw rechazo("el mensaje no cumple el sobre del contrato (versión 1 con data)");
            }
            return sobre;
        } catch (IOException e) {
            throw rechazo("el mensaje no es JSON: " + e.getMessage());
        }
    }

    private static UUID uuid(JsonNode nodo, String campo) {
        try {
            return UUID.fromString(nodo.path(campo).asText());
        } catch (IllegalArgumentException e) {
            throw rechazo("falta o no es uuid: " + campo);
        }
    }

    private static UUID uuidOpcional(JsonNode nodo, String campo) {
        return nodo.path(campo).isTextual() ? uuid(nodo, campo) : null;
    }

    /** F10: dinero como string decimal con 2 decimales; nunca número. */
    private static Dinero dinero(JsonNode data, String campo, boolean requerido) {
        JsonNode valor = data.path(campo);
        if (valor.isMissingNode() || valor.isNull()) {
            if (requerido) {
                throw rechazo("falta " + campo);
            }
            return Dinero.de("0");
        }
        if (!valor.isTextual() || !valor.asText().matches("^\\d+\\.\\d{2}$")) {
            throw rechazo(campo + " debe ser un decimal como string con 2 decimales");
        }
        return Dinero.de(valor.asText());
    }

    private static AmqpRejectAndDontRequeueException rechazo(String motivo) {
        log.warn("Comando de reserva rechazado a la DLQ: {}", motivo);
        return new AmqpRejectAndDontRequeueException(motivo);
    }
}
