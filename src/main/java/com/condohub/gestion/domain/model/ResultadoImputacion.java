package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.util.List;

/** Cómo se repartió un pago entre los cargos (F7): imputaciones en orden FIFO y excedente que queda como saldo a favor. */
public record ResultadoImputacion(List<Imputacion> imputaciones, Dinero excedente) {

    public ResultadoImputacion {
        imputaciones = List.copyOf(imputaciones);
    }

    /** Total aplicado a cargos. */
    public Dinero imputado() {
        return Dinero.de(imputaciones.stream().map(i -> i.monto().monto()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
