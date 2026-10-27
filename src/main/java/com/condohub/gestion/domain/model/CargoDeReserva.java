package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.time.LocalDate;

/** Un cargo que genera una reserva de área común (R5): el uso del área o la garantía del salón. */
public record CargoDeReserva(Tipo tipo, String concepto, Dinero monto, LocalDate fechaVencimiento) {

    /** Mismos nombres que el tipo {@code tipo_cargo} de la base. */
    public enum Tipo {
        USO_AREA,
        GARANTIA
    }
}
