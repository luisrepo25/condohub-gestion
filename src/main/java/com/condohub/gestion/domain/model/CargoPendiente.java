package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Cargo de una unidad con saldo por pagar, tal como lo necesita la imputación de pagos (F7). */
public record CargoPendiente(UUID id, Dinero saldo, LocalDate fechaVencimiento, Instant creadoEn) {

    public CargoPendiente {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(saldo, "saldo");
        Objects.requireNonNull(fechaVencimiento, "fechaVencimiento");
        Objects.requireNonNull(creadoEn, "creadoEn");
    }
}
