package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.List;
import java.util.UUID;

/** Saga de reserva, paso 2 (R5, S5): aplicar los cargos de una reserva y responder {@code finanzas.CargoAplicado}. */
public interface AplicarCargosReservaUseCase {

    /** Datos del comando {@code reservas.AplicarCargosReserva}; {@code eventId} hace idempotente su proceso (S5). */
    record Comando(UUID eventId, UUID sagaId, UUID reservaId, UUID unidadId, String areaComun, Dinero costoUso, Dinero garantia) {}

    /**
     * @param procesado false si ese {@code eventId} ya se había procesado (repetido: no se hace nada otra vez)
     * @param cargoIds  cargos de la reserva (vacío si la reserva no cuesta nada)
     */
    record Resultado(boolean procesado, List<UUID> cargoIds) {}

    Resultado aplicar(Comando comando);
}
