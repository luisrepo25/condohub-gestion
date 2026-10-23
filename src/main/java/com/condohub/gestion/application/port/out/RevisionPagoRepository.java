package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.model.CargoPendiente;
import com.condohub.gestion.domain.model.Pago;
import com.condohub.gestion.domain.model.PagoPorRevisar;
import com.condohub.gestion.domain.model.ResultadoImputacion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Conciliación de pagos (F7, F8). Las lecturas bloquean las filas hasta el fin de la transacción. */
public interface RevisionPagoRepository {

    Optional<PagoPorRevisar> porIdParaRevisar(UUID pagoId);

    /** Cargos con saldo de la unidad (estado PENDIENTE). */
    List<CargoPendiente> cargosPendientes(UUID unidadId);

    /** Marca el pago CONFIRMADO, guarda las imputaciones, baja el saldo de cada cargo y emite el recibo. */
    long confirmar(UUID pagoId, String adminSub, ResultadoImputacion resultado);

    void rechazar(UUID pagoId, String adminSub, String motivo);

    Pago pago(UUID pagoId);
}
