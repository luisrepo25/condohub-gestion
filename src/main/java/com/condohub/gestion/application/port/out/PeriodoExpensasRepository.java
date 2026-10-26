package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.model.PeriodoExpensas;
import com.condohub.gestion.domain.valueobject.Periodo;

public interface PeriodoExpensasRepository {

    boolean existe(Periodo periodo);

    /** Guarda el periodo emitido y un cargo {@code EXPENSA} por unidad, en la misma transacción. */
    void guardar(PeriodoExpensas periodoExpensas);
}
