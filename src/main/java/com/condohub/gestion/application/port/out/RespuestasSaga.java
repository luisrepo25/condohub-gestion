package com.condohub.gestion.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Respuestas de Gestión al orquestador de la saga (Reservas), por Outbox (S4): se guardan en la transacción del caso
 * de uso. El {@code eventId} de la respuesta sale del comando que la originó, así un reintento nunca la duplica.
 */
public interface RespuestasSaga {

    /** {@code finanzas.CargoAplicado} */
    void cargoAplicado(UUID comandoEventId, UUID sagaId, UUID reservaId, UUID unidadId, List<UUID> cargoIds);

    /** {@code finanzas.CargoAnulado} */
    void cargoAnulado(UUID comandoEventId, UUID sagaId, UUID reservaId, UUID unidadId, List<UUID> cargoIds);
}
