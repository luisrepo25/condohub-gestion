package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.AlcanceAnulacion;
import java.util.List;
import java.util.UUID;

/**
 * Compensación del paso 2 y cancelaciones (R6, R7, F6): anular cargos de una reserva con nota de crédito y responder
 * {@code finanzas.CargoAnulado}.
 */
public interface AnularCargosReservaUseCase {

    record Comando(UUID eventId, UUID sagaId, UUID reservaId, UUID unidadId, AlcanceAnulacion alcance) {}

    /**
     * @param procesado false si ese {@code eventId} ya se había procesado (S5)
     * @param cargoIds  cargos anulados en esta operación
     */
    record Resultado(boolean procesado, List<UUID> cargoIds) {}

    Resultado anular(Comando comando);
}
