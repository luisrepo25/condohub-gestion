package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.PeriodoExpensas;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.Periodo;

public interface EmitirExpensasUseCase {

    /** Emite las expensas del periodo a todas las unidades activas (F2, F3, F4). Un periodo se emite una sola vez. */
    PeriodoExpensas emitir(String adminSub, Periodo periodo, Dinero presupuesto);
}
