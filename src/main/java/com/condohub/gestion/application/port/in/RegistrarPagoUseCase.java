package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.Pago;
import com.condohub.gestion.domain.model.Solicitante;
import java.math.BigDecimal;
import java.util.UUID;

public interface RegistrarPagoUseCase {

    /**
     * Registra un pago {@code PENDIENTE} (F8, F9). El RESIDENTE solo para su unidad (F1). Repetir la misma
     * idempotencyKey devuelve el mismo pago que la primera vez (O3).
     */
    Pago registrar(Solicitante solicitante, Comando comando);

    /** Datos tal como llegan del cliente; el caso de uso los valida al construir los objetos de valor. */
    record Comando(
            UUID unidadId,
            BigDecimal monto,
            String fechaPago,
            String banco,
            String idempotencyKey,
            String horaDispositivo) {}
}
