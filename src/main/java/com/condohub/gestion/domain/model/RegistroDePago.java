package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.IdempotencyKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Un pago recién registrado a partir del comprobante (F8). Nace siempre {@link EstadoPago#PENDIENTE}: solo el ADMIN lo
 * confirma o rechaza después, nunca el cliente (F9). Lleva la clave de idempotencia y la hora del dispositivo (O1).
 */
public record RegistroDePago(
        UUID unidadId,
        Dinero monto,
        LocalDate fechaPago,
        String banco,
        IdempotencyKey idempotencyKey,
        String registradoPor,
        Instant horaDispositivo) {

    static final int BANCO_LARGO_MAXIMO = 60;

    public RegistroDePago {
        Objects.requireNonNull(unidadId, "unidadId");
        Objects.requireNonNull(monto, "monto");
        Objects.requireNonNull(fechaPago, "fechaPago");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(registradoPor, "registradoPor");
        if (monto.monto().signum() <= 0) {
            throw new ReglaNegocioVioladaException("F10", "El monto del pago debe ser mayor a 0");
        }
        banco = banco == null || banco.isBlank() ? null : banco.trim();
        if (banco != null && banco.length() > BANCO_LARGO_MAXIMO) {
            throw new ReglaNegocioVioladaException(
                    "F8", "El banco admite como máximo " + BANCO_LARGO_MAXIMO + " caracteres");
        }
    }

    /** F8: la fecha del comprobante no puede ser futura. */
    public static void exigirFechaNoFutura(LocalDate fechaPago, LocalDate hoy) {
        if (fechaPago.isAfter(hoy)) {
            throw new ReglaNegocioVioladaException(
                    "F8", "La fecha del comprobante (" + fechaPago + ") no puede ser futura");
        }
    }

    /** F8, F9: el pago registrado queda pendiente de conciliación. */
    public EstadoPago estado() {
        return EstadoPago.PENDIENTE;
    }
}
